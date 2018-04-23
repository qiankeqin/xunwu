package com.ximua.xunwu.service.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Longs;
import com.ximua.xunwu.base.HouseSort;
import com.ximua.xunwu.base.RentValueBlock;
import com.ximua.xunwu.entity.House;
import com.ximua.xunwu.entity.HouseDetail;
import com.ximua.xunwu.entity.HouseTag;
import com.ximua.xunwu.entity.SupportAddress;
import com.ximua.xunwu.repository.HouseDetailRepository;
import com.ximua.xunwu.repository.HouseRepository;
import com.ximua.xunwu.repository.HouseTagRepository;
import com.ximua.xunwu.repository.SupportAddressRepository;
import com.ximua.xunwu.service.ServiceResult;
import com.ximua.xunwu.service.house.IAddressService;
import com.ximua.xunwu.web.controller.user.ServiceMultiResult;
import com.ximua.xunwu.web.form.RentSearch;
import org.assertj.core.util.Lists;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeAction;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequestBuilder;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.DeleteByQueryRequestBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * 搜索索引接口实现类
 */
@Service
public class SearchServiceImpl implements ISearchService {
    private static final Logger logger = LoggerFactory.getLogger(ISearchService.class);
    //ElasticSearch配置
    private static final String INDEX_NAME = "xunwu";
    private static final String INDEX_TYPE = "house";
    //Kafka配置
    private static final String INDEX_TOPIC = "house_build";

    @Autowired
    private HouseRepository houseRepository;
    @Autowired
    private HouseDetailRepository houseDetailRepository;
    @Autowired
    private HouseTagRepository houseTagRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private TransportClient esClient;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SupportAddressRepository supportAddressRepository;
    @Autowired
    private IAddressService addressService;
    //声明kafka到类型为String，String，将消息声明序列化成String
    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    //监听topic，传入消息体content（String类型）
    @KafkaListener(topics = INDEX_TOPIC)
    private void handlerMessage(String content){
        //我们需要分别index和remove
        try {
            //反序列化
            HouseIndexMessage message = objectMapper.readValue(content, HouseIndexMessage.class);
            //判断是那种类型到message
            switch(message.getOperation()){
                case HouseIndexMessage.INDEX:
                    this.createOrUpdateIndex(message);
                    break;
                case HouseIndexMessage.REMOVE:
                    this.removeIndex(message);
                    break;
                default:
                    logger.warn("Not support message content "+content);
                    break;
            }
        } catch (IOException e) {
            logger.error("Cannot parse json for "+content,e);
        }
    }

    /**
     * 根据kafka中接收到的消息创建和更新索引
     * @param message
     */
    private void createOrUpdateIndex(HouseIndexMessage message){
        Long houseId = message.getHouseId();
        House house = houseRepository.findOne(houseId);
        if(house == null){
            logger.error("Index house {} does not exist!",houseId);
            this.index(houseId,message.getRetry()+1);
            return;
        }
        HouseIndexTemplate indexTemplate = new HouseIndexTemplate();
        modelMapper.map(house,indexTemplate);
        HouseDetail detail = houseDetailRepository.findAllByHouseId(houseId);
        if(detail == null){
            //TODO 异常情况

        }
        modelMapper.map(detail,indexTemplate);

        //查询地理位置
        SupportAddress city = supportAddressRepository.findByEnNameAndLevel(house.getCityEnName(), SupportAddress.Level.CITY.getValue());
        SupportAddress region = supportAddressRepository.findByEnNameAndLevel(house.getRegionEnName(),SupportAddress.Level.REGION.getValue());

        String address = city.getCnName() + region.getCnName() + house.getStreet() + house.getDistrict() + detail.getDetailAddress();

        ServiceResult<BaiduMapLocation> location = addressService.getBaiduMapLocation(city.getCnName(), address);
        //索引location地址
        if(!location.isSuccess()){
            this.index(message.getHouseId(),message.getRetry() + 1);
            return;
        }
        indexTemplate.setLocation(location.getResult());

        List<HouseTag> tags = houseTagRepository.findAllByHouseId(houseId);
        if(tags!= null && tags.size()>0){
            List<String> tagStrings = new ArrayList<>();
            tags.forEach(houseTag -> {
                tagStrings.add(houseTag.getName());
            });
            indexTemplate.setTags(tagStrings);
        }

        SearchRequestBuilder requestBuilder = this.esClient.prepareSearch(INDEX_NAME).setTypes(INDEX_TYPE)
                .setQuery(QueryBuilders.termQuery(HouseIndexKey.HOUSE_ID, houseId));
        logger.debug(requestBuilder.toString());

        SearchResponse searchResponse =  requestBuilder.get();
        long totalHit = searchResponse.getHits().getTotalHits();
        boolean success = false;
        if(totalHit == 0){ //如果这条数据不存在,create
            success = create(indexTemplate);
        } else if(totalHit == 1){ //如果数据存在一条,update
            String esId = searchResponse.getHits().getAt(0).getId();
            success = update(esId,indexTemplate);
        } else{ //如果数据存在多条，那么这是有问题都，所以先删除再进行创建,deleteAndCreate
            success = deleteAndCreate(totalHit,indexTemplate);
        }
    }

    /**
     * 根据kafka中到message，执行移除索引方法
     * @param message
     */
    private void removeIndex(HouseIndexMessage message){
        Long houseId = message.getHouseId();
        DeleteByQueryRequestBuilder builder = DeleteByQueryAction.INSTANCE
                .newRequestBuilder(esClient)
                .filter(QueryBuilders.termQuery(HouseIndexKey.HOUSE_ID, houseId))
                .source(INDEX_NAME);
        logger.debug("Delete by query for house : "+builder);
        BulkByScrollResponse response = builder.get();
        long deleted = response.getDeleted();
        logger.debug("Delete total "+deleted);

        //数据删除失败，消息消费失败
        if(deleted<=0){
            this.remove(houseId,message.getRetry()+1);
        }
    }

    //索引方法
    private void index(Long houseId,int retry){
        //重索引机制
        if(retry > HouseIndexMessage.MAX_RETRY){
            logger.error("Retry index times over 3 for house: "+houseId + ". Please check it!");
            return;
        }

        HouseIndexMessage message = new HouseIndexMessage(houseId,HouseIndexMessage.INDEX,retry);
        try {
            kafkaTemplate.send(INDEX_TOPIC,objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            logger.error("Json encode error for "+message);
        }
    }

    /**
     * 索引房源数据
     * @param houseId
     */
    @Override
    public boolean index(Long houseId) {
        this.index(houseId,0);
        return true;
    }

    private boolean create(HouseIndexTemplate indexTemplate){
        if(!updateSuggest(indexTemplate)){
            return false;
        }
        try {
            IndexResponse response = this.esClient.prepareIndex(INDEX_NAME, INDEX_TYPE)
                    .setSource(objectMapper.writeValueAsBytes(indexTemplate), XContentType.JSON).get();
            logger.debug("Create index with house: "+indexTemplate.getHouseId());
            if(response.status() == RestStatus.CREATED){
                return true;
            }else{
                return false;
            }
        } catch (JsonProcessingException e) {
            logger.error("Error to index house "+indexTemplate.getHouseId(),e);
            return false;
        }
    }

    private boolean update(String esId,HouseIndexTemplate indexTemplate){
        if(!updateSuggest(indexTemplate)){
            return false;
        }
        try {
            UpdateResponse response = this.esClient.prepareUpdate(INDEX_NAME, INDEX_TYPE, esId)
                    .setDoc(objectMapper.writeValueAsBytes(indexTemplate), XContentType.JSON).get();
            logger.debug("Update index with house: "+indexTemplate.getHouseId());
            if(response.status() == RestStatus.OK){
                return true;
            }else{
                return false;
            }
        } catch (JsonProcessingException e) {
            logger.error("Error to index house "+indexTemplate.getHouseId(),e);
            return false;
        }
    }

    private boolean deleteAndCreate(long totalHit, HouseIndexTemplate indexTemplate){
        DeleteByQueryRequestBuilder builder = DeleteByQueryAction.INSTANCE
                .newRequestBuilder(esClient)
                .filter(QueryBuilders.termQuery(HouseIndexKey.HOUSE_ID, indexTemplate.getHouseId()))
                .source(INDEX_NAME);
        logger.debug("Delete by query for house: "+ builder);
        BulkByScrollResponse response = builder.get();
        long deleted = response.getDeleted();
        if(deleted != totalHit){
            logger.warn("Need delete {},but {} was deleted",totalHit,deleted);
            return false;
        } else {
            return create(indexTemplate);
        }
    }

    /**
     * 移除房源索引
     * @param houseId
     */
    @Override
    public void remove(Long houseId) {
        remove(houseId,0);
    }

    @Override
    public ServiceMultiResult<Long> query(RentSearch rentSearch) {
        //简单bool查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.filter(
                QueryBuilders.termQuery(HouseIndexKey.CITY_EN_NAME,rentSearch.getCityEnName())
        );
        if(rentSearch.getRegionEnName()!=null && !"*".equals(rentSearch.getRegionEnName())){
            boolQuery.filter(
                    QueryBuilders.termQuery(HouseIndexKey.REGION_EN_NAME,rentSearch.getRegionEnName())
            );
        }

        //面积查询
        RentValueBlock area = RentValueBlock.matchArea(rentSearch.getAreaBlock());
        //如果查询面积不是ALL查询
        if(!RentValueBlock.ALL.equals(area)){
            RangeQueryBuilder rangeQueryBuilder =  QueryBuilders.rangeQuery(HouseIndexKey.AREA);
            if(area.getMax()>0){
                rangeQueryBuilder.lte(area.getMax());
            }
            if(area.getMin()>0){
                rangeQueryBuilder.gte(area.getMin());
            }
            boolQuery.filter(rangeQueryBuilder);
        }


        //价格查询
        RentValueBlock price = RentValueBlock.matchPrice(rentSearch.getPriceBlock());
        System.out.println("测试价格+++++++++++++++++++++++："+rentSearch.getPriceBlock());
        //如果查询面积不是ALL查询
        if(!RentValueBlock.ALL.equals(price)){
            RangeQueryBuilder rangeQueryBuilder =  QueryBuilders.rangeQuery(HouseIndexKey.PRICE);
            if(price.getMax()>0){
                rangeQueryBuilder.lte(price.getMax());
            }
            if(price.getMin()>0){
                rangeQueryBuilder.gte(price.getMin());
            }
            boolQuery.filter(rangeQueryBuilder);
        }

        //具体朝向查询
        if(rentSearch.getDirection()>0){
            boolQuery.filter(
                    QueryBuilders.termQuery(HouseIndexKey.DIRECTION,rentSearch.getDirection())
            );
        }

        //租赁方式
        if(rentSearch.getRentWay()>-1){
            boolQuery.filter(
                    QueryBuilders.termQuery(HouseIndexKey.RENT_WAY,rentSearch.getRentWay())
            );
        }

        //关键词匹配 灵活运用boolQuery.must  boolQuery.should
        //设置title的搜索权重,默认是1
        boolQuery.should(QueryBuilders.matchQuery(HouseIndexKey.TITLE,rentSearch.getKeywords()).boost(2.0f));
        //下面可以排除title了，默认都是1
        boolQuery.should(
                QueryBuilders.multiMatchQuery(rentSearch.getKeywords(),
                        //HouseIndexKey.TITLE,
                        HouseIndexKey.TRAFFIC,
                        HouseIndexKey.DISTRICT,
                        HouseIndexKey.ROUND_SERVICE,
                        HouseIndexKey.SUBWAY_LINE_NAME,
                        HouseIndexKey.SUBWAY_STATION_NAME
                ));


            //设置查询条件，生成SearchRequestBuilder
            SearchRequestBuilder requestBuilder = this.esClient.prepareSearch(INDEX_NAME)
                    .setTypes(INDEX_TYPE)
                    .setQuery(boolQuery)
                    .addSort(HouseSort.getSortKey(rentSearch.getOrderBy()),
                            SortOrder.fromString(rentSearch.getOrderDirection())
                    )
                    .setFrom(rentSearch.getStart())
                    .setSize(rentSearch.getSize())
                    .setFetchSource(HouseIndexKey.HOUSE_ID,null);
        logger.debug(requestBuilder.toString());

        List<Long> houseIds = new ArrayList<>();
        SearchResponse response = requestBuilder.get();
        if(response.status() != RestStatus.OK){
            logger.warn("Search status is not ok for "+requestBuilder);
            return new ServiceMultiResult<>(0,houseIds);
        }

        //循环遍历Hit，打印所有查询到到数据
        for(SearchHit hit : response.getHits()){
            houseIds.add(
                    Longs.tryParse(String.valueOf(hit.getSourceAsMap().get(HouseIndexKey.HOUSE_ID)))
            );
        }

        return new ServiceMultiResult<>(response.getHits().getTotalHits(),houseIds);
    }

    /**
     * 根据输入的前缀进行提示
     * @param prefix
     * @return
     */
    @Override
    public ServiceResult<List<String>> suggest(String prefix) {
        //对suggest字段进行搜索
        CompletionSuggestionBuilder suggestion = SuggestBuilders.completionSuggestion("suggest").prefix(prefix).size(5);
        SuggestBuilder suggestBuilder = new SuggestBuilder();
        //为suggest起名字（任意）
        suggestBuilder.addSuggestion("autocomplete",suggestion);
        SearchRequestBuilder requestBuilder = this.esClient.prepareSearch(INDEX_NAME)
                .setTypes(INDEX_TYPE)
                .suggest(suggestBuilder);
        logger.debug(requestBuilder.toString());
        //执行查询
        SearchResponse response = requestBuilder.get();
        //获取suggest结果
        Suggest suggest = response.getSuggest();
        Suggest.Suggestion result = suggest.getSuggestion("autocomplete");

        int maxSuggest = 0;
        Set<String> suggestSet = new HashSet<>();
        //下面的代码是去重的效果
        for(Object term : result.getEntries())
        {
            if(term instanceof CompletionSuggestion.Entry){
                CompletionSuggestion.Entry item = (CompletionSuggestion.Entry) term;
                if(item.getOptions().isEmpty()){
                    continue;
                }

                for(CompletionSuggestion.Entry.Option option:item.getOptions()){
                    String tip = option.getText().string();
                    if(suggestSet.contains(tip)){
                        continue;
                    }
                    suggestSet.add(tip);
                    maxSuggest++;
                }
            }
            if(maxSuggest>5){
                break;
            }
        }
        ArrayList<String> suggests = Lists.newArrayList(suggestSet.toArray(new String[]{}));
        return ServiceResult.of(suggests);
    }

    /**
     * 聚合特定小区的房源数量
     * @param cityEnName
     * @param regionEnName
     * @param district
     * @return
     */
    @Override
    public ServiceResult<Long> aggregateDistrictHouse(String cityEnName, String regionEnName ,String district) {
        //先大范围筛选，然后小范围聚合
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery(HouseIndexKey.CITY_EN_NAME,cityEnName))
                .filter(QueryBuilders.termQuery(HouseIndexKey.REGION_EN_NAME,regionEnName))
                .filter(QueryBuilders.termQuery(HouseIndexKey.DISTRICT,district));
        SearchRequestBuilder requestBuilder = this.esClient.prepareSearch(INDEX_NAME)
                .setTypes(INDEX_TYPE)
                .setQuery(queryBuilder)
                .addAggregation(
                        AggregationBuilders.terms(HouseIndexKey.AGG_DISTRICT)//起名
                                .field(HouseIndexKey.DISTRICT)//对那个字段进行聚合
                ).setSize(0);//不需要原始数据，只需要聚合对数据
        logger.debug(requestBuilder.toString());

        SearchResponse response = requestBuilder.get();
        if(response.status() == RestStatus.OK){
            //获取聚合结果对数据
            Terms terms = response.getAggregations().get(HouseIndexKey.AGG_DISTRICT);
            if(terms.getBuckets()!=null && terms.getBuckets().size()!=0){
                return ServiceResult.of(terms.getBucketByKey(district).getDocCount());
            }
        }else{
            logger.warn("Failed to Aggregate for "+HouseIndexKey.AGG_DISTRICT);
        }
        return ServiceResult.of(0L);
    }

    /**
     * 聚合bucket的数据
     * @param cityEnName
     * @return
     */
    @Override
    public ServiceMultiResult<HouseBucketDTO> magAggregate(String cityEnName) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.filter(QueryBuilders.termQuery(HouseIndexKey.CITY_EN_NAME,cityEnName));

        TermsAggregationBuilder aggBuilder = AggregationBuilders.terms(HouseIndexKey.AGG_REGION)
                .field(HouseIndexKey.REGION_EN_NAME);
        SearchRequestBuilder requestBuilder = this.esClient.prepareSearch(INDEX_NAME)
                .setTypes(INDEX_TYPE)
                .setQuery(boolQuery)
                .addAggregation(aggBuilder);

        logger.debug(requestBuilder.toString());
        SearchResponse response = requestBuilder.get();
        List<HouseBucketDTO> buckets = new ArrayList<>();
        if(response.status() != RestStatus.OK){
            logger.warn("Aggregate status is not ok for "+requestBuilder);
            return new ServiceMultiResult<>(0,buckets);
        }
        Terms terms = response.getAggregations().get(HouseIndexKey.AGG_REGION);
        for(Terms.Bucket bucket : terms.getBuckets()){
            buckets.add(new HouseBucketDTO(bucket.getKeyAsString(),bucket.getDocCount()));
        }
        return new ServiceMultiResult<>(response.getHits().getTotalHits(),buckets);
    }

    /**
     * 地图搜索
     * @param cityEnName
     * @param orderBy
     * @param orderDirection
     * @param start
     * @param size
     * @return
     */
    @Override
    public ServiceMultiResult<Long> mapQuery(String cityEnName, String orderBy, String orderDirection, int start, int size) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.filter(QueryBuilders.termQuery(HouseIndexKey.CITY_EN_NAME,cityEnName));
        SearchRequestBuilder searchRequestBuilder = this.esClient.prepareSearch(INDEX_NAME)
                .setTypes(INDEX_TYPE)
                .setQuery(boolQuery)
                .addSort(HouseSort.getSortKey(orderBy), SortOrder.fromString(orderDirection))
                .setFrom(start)
                .setSize(size);
        List<Long> houseIds = new ArrayList<>();
        SearchResponse response = searchRequestBuilder.get();
        if(response.status() != RestStatus.OK){
            logger.error("Search status is not ok for "+searchRequestBuilder);
            return new ServiceMultiResult<>(0,houseIds);
        }
        response.getHits().forEach(
                hit -> {
                    houseIds.add(Longs.tryParse( String.valueOf(hit.getSource().get(HouseIndexKey.HOUSE_ID))));
                }
        );
        return new ServiceMultiResult<>(response.getHits().getTotalHits(),houseIds);
    }

    /**
     * 每次在create或者update的时候，对suggest进行更新
     * @param indexTemplate
     * @return
     */
//    private boolean updateSuggest(HouseIndexTemplate indexTemplate){
//        System.out.println(indexTemplate);
//        //对分词接口请，对需要进行分词对字段进行请求
//        AnalyzeRequestBuilder requestBuilder = new AnalyzeRequestBuilder(
//                this.esClient, AnalyzeAction.INSTANCE,INDEX_NAME,indexTemplate.getTitle(),indexTemplate.getLayoutDesc(),
//                indexTemplate.getRoundService(),indexTemplate.getDescription(),indexTemplate.getSubwayLineName(),
//                indexTemplate.getSubwayStationEnName()
//                );
//        requestBuilder.setAnalyzer("ik_smart");//ik_smart
//        //获取分词结果
//        AnalyzeResponse response = requestBuilder.get();
//        //获取分词结果中对tokens
//        List<AnalyzeResponse.AnalyzeToken> tokens = response.getTokens();
//        //如果不能分词
//        if(tokens == null){
//            logger.warn("Can not analyze token for house : "+indexTemplate.getHouseId());
//            return false;
//        }
//        //如果能够分，获取符合条件对suggest term
//        List<HouseSuggest> suggests = new ArrayList<>();
//        for(AnalyzeResponse.AnalyzeToken token : tokens){
//            //排除数字类型 && 小于两个字符的分词（根据需求）
//            if("<NUM>".equals(token.getType()) || token.getTerm().length() < 2){
//                continue;
//            }
//            HouseSuggest suggest = new HouseSuggest();
//            suggest.setInput(token.getTerm());//添加获取到的词汇
//            //这里还可以设置权重
//            suggests.add(suggest);
//        }
//        //定制化数据:小区数据自动补全
//        HouseSuggest suggest = new HouseSuggest();
//        suggest.setInput(indexTemplate.getDistrict());
//        suggests.add(suggest);
//
//        //设置indexTemplate中对suggest字段
//        indexTemplate.setSuggest(suggests);
//        return true;
//    }


    private boolean updateSuggest(HouseIndexTemplate indexTemplate) {
        AnalyzeRequestBuilder requestBuilder = new AnalyzeRequestBuilder(
                this.esClient, AnalyzeAction.INSTANCE, INDEX_NAME, indexTemplate.getTitle(),
                indexTemplate.getLayoutDesc(), indexTemplate.getRoundService(),
                indexTemplate.getDescription(), indexTemplate.getSubwayLineName(),
                indexTemplate.getSubwayStationName());

        requestBuilder.setAnalyzer("ik_smart");

        AnalyzeResponse response = requestBuilder.get();
        List<AnalyzeResponse.AnalyzeToken> tokens = response.getTokens();
        if (tokens == null) {
            logger.warn("Can not analyze token for house: " + indexTemplate.getHouseId());
            return false;
        }

        List<HouseSuggest> suggests = new ArrayList<>();
        for (AnalyzeResponse.AnalyzeToken token : tokens) {
            // 排序数字类型 & 小于2个字符的分词结果
            if ("<NUM>".equals(token.getType()) || token.getTerm().length() < 2) {
                continue;
            }

            HouseSuggest suggest = new HouseSuggest();
            suggest.setInput(token.getTerm());
            suggests.add(suggest);
        }

        // 定制化小区自动补全
        HouseSuggest suggest = new HouseSuggest();
        suggest.setInput(indexTemplate.getDistrict());
        suggests.add(suggest);

        indexTemplate.setSuggest(suggests);
        return true;
    }


    private void remove(Long houseId,int retry){
        if(retry > HouseIndexMessage.MAX_RETRY){
            logger.error("Rety remove times over 3 for house: "+houseId + " Please check it!");
            return;
        }
        HouseIndexMessage message = new HouseIndexMessage(houseId,HouseIndexMessage.REMOVE,retry);
        try {
            this.kafkaTemplate.send(INDEX_TOPIC,objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            logger.error("Cannot encode json for "+message,e);
        }
    }
}
