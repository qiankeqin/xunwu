package com.ximua.xunwu.config;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * ElasticSearch配置
 */
@Configuration
public class ElasticSearchConfig {
    @Bean
    public TransportClient esClient() throws UnknownHostException {
        Settings settings = Settings.builder()
                .put("cluster.name", "elasticsearch")
//                .put("cluster.name", "elasticsearch")
                .put("client.transport.sniff", true)
                .build();

        //注意：使用的ES版本和引入的ElasticSearch的maven版本需要一致
// ES5.6.x Java Api
//        InetSocketTransportAddress master = new InetSocketTransportAddress(
//                InetAddress.getByName("192.168.43.199"), 9300
////          InetAddress.getByName("10.99.207.76"), 8999
//        );

        //ES6.2 Java Api
        TransportClient client = new PreBuiltTransportClient(settings)
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"),9300));
        return client;
    }
}
