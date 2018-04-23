package com.ximua.xunwu.base;


import com.google.common.collect.Sets;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * 排序生成，各维度排序实体
 */
public class HouseSort {
    //默认排序字段
    public static final String DEFAULT_SORT_KEY = "lastUpdateTime";

    //按到地铁到距离排序
    public static final String DISTANCE_TO_SUBWAY_KEY = "distanceToSubway";

    //排序集合
    private static final Set<String> SORT_KEYS = Sets.newHashSet(
        DEFAULT_SORT_KEY,
            "createTime",
            "price",
            "area",
            DISTANCE_TO_SUBWAY_KEY
    );

    //生成排序实体Sort
    public static Sort generateSort(String key,String directionKey){
        key = getSortKey(key);
        Sort.Direction direction = Sort.Direction.fromStringOrNull(directionKey);
        if(direction==null){
            direction = Sort.Direction.DESC;
        }
        return new Sort(direction,key);
    }

    /**
     * 获取key
     * @param key
     * @return
     */
    public static String getSortKey(String key){
        if(!SORT_KEYS.contains(key)){
            key = DEFAULT_SORT_KEY;
        }
        return key;
    }
}
