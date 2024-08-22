package io.knifer.freebox.websocket.service;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.AbsSortJson;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.FileUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.lzy.okgo.OkGo;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import org.java_websocket.WebSocket;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import io.knifer.freebox.constant.MessageCodes;
import io.knifer.freebox.model.c2s.RegisterInfo;
import io.knifer.freebox.model.common.Message;
import io.knifer.freebox.util.GsonUtil;
import io.knifer.freebox.util.HttpUtil;

/**
 * WebSocket服务
 * @author knifer
 */
public class WSService {

    private final WebSocket connection;

    public WSService(WebSocket connection) {
        this.connection = connection;
    }

    public void register() {
        send(Message.oneWay(
                MessageCodes.REGISTER,
                RegisterInfo.of("tvbox-default")
        ));
    }

    public void sendSourceBeanList(String topicId) {
        send(Message.oneWay(
                MessageCodes.GET_SOURCE_BEAN_LIST_RESULT,
                ApiConfig.get().getSourceBeanList(),
                topicId
        ));
    }

    public void sendHomeContent(String topicId, SourceBean source) {
        send(Message.oneWay(
                MessageCodes.GET_HOME_CONTENT_RESULT,
                getHomeContent(source.getKey()),
                topicId
        ));
    }

    /**
     * 改写自SourceViewModel中的getSort方法
     * @see com.github.tvbox.osc.viewmodel.SourceViewModel
     * @param sourceKey sourceKey
     * @return homeContent信息
     */
    private AbsSortXml getHomeContent(String sourceKey) {
        if (sourceKey == null) {
            return null;
        }
        SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
        int type = sourceBean.getType();
        AbsSortXml sortXml = null;
        String content;
        switch (type) {
            case 3:
                try {
                    Spider sp = ApiConfig.get().getCSP(sourceBean);
                    String sortJson = sp.homeContent(true);
                    sortXml = sortJson(sortJson);
                } catch (Exception ignored) {}
                break;
            case 0:
            case 1:
                content = HttpUtil.getStringBody(OkGo.<String>get(sourceBean.getApi())
                        .tag(sourceBean.getKey() + "_sort")
                );
                if (type == 0) {
                    sortXml = sortXml(content);
                } else {
                    sortXml = sortJson(content);
                }
                // 到此为止即可（只取对应源站主页推荐数据，不考虑将豆瓣推荐结果发送给FreeBox）
                break;
            case 4:
                String extend = getFixUrl(sourceBean.getExt());

                if (URLEncoder.encode(extend).length() < 1000) {
                    content = HttpUtil.getStringBody(OkGo.<String>get(sourceBean.getApi())
                            .tag(sourceBean.getKey() + "_sort")
                            .params("filter", "true")
                            .params("extend", extend)
                    );
                    sortXml = sortJson(content);
                }
                // 到此为止即可（同上）
                break;
            default:
                break;
        }

        return sortXml;
    }

    private AbsSortXml sortJson(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            AbsSortJson sortJson = new Gson().fromJson(obj, new TypeToken<AbsSortJson>() {
            }.getType());
            AbsSortXml data = sortJson.toAbsSortXml();
            try {
                if (obj.has("filters")) {
                    LinkedHashMap<String, ArrayList<MovieSort.SortFilter>> sortFilters = new LinkedHashMap<>();
                    JsonObject filters = obj.getAsJsonObject("filters");
                    for (String key : filters.keySet()) {
                        ArrayList<MovieSort.SortFilter> sortFilter = new ArrayList<>();
                        JsonElement one = filters.get(key);
                        if (one.isJsonObject()) {
                            sortFilter.add(getSortFilter(one.getAsJsonObject()));
                        } else {
                            for (JsonElement ele : one.getAsJsonArray()) {
                                sortFilter.add(getSortFilter(ele.getAsJsonObject()));
                            }
                        }
                        sortFilters.put(key, sortFilter);
                    }
                    for (MovieSort.SortData sort : data.classes.sortList) {
                        if (sortFilters.containsKey(sort.id) && sortFilters.get(sort.id) != null) {
                            sort.filters = sortFilters.get(sort.id);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private MovieSort.SortFilter getSortFilter(JsonObject obj) {
        String key = obj.get("key").getAsString();
        String name = obj.get("name").getAsString();
        JsonArray kv = obj.getAsJsonArray("value");
        LinkedHashMap<String, String> values = new LinkedHashMap<>();

        for (JsonElement ele : kv) {
            JsonObject ele_obj = ele.getAsJsonObject();
            String values_key=ele_obj.has("n")?ele_obj.get("n").getAsString():"";
            String values_value=ele_obj.has("v")?ele_obj.get("v").getAsString():"";
            values.put(values_key, values_value);
        }
        MovieSort.SortFilter filter = new MovieSort.SortFilter();
        filter.key = key;
        filter.name = name;
        filter.values = values;

        return filter;
    }

    private AbsSortXml sortXml(String xml) {
        try {
            XStream xstream = new XStream(new DomDriver());
            xstream.autodetectAnnotations(true);
            xstream.processAnnotations(AbsSortXml.class);
            xstream.ignoreUnknownElements();
            AbsSortXml data = (AbsSortXml) xstream.fromXML(xml);
            for (MovieSort.SortData sort : data.classes.sortList) {
                if (sort.filters == null) {
                    sort.filters = new ArrayList<>();
                }
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private String getFixUrl(String content){
        if (content.startsWith("http://127.0.0.1")) {
            String path = content.replaceAll("^http.+/file/", FileUtils.getRootPath()+"/");
            path = path.replaceAll("localhost/", "/");
            content = FileUtils.readFileToString(path,"UTF-8");
        }

        return content;
    }

    private void send(Object obj) {
        connection.send(GsonUtil.toJson(obj));
    }
}
