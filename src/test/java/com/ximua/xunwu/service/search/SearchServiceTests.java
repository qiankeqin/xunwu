package com.ximua.xunwu.service.search;

import com.ximua.xunwu.ApplicationTests;
import com.ximua.xunwu.XunwuApplication;
import com.ximua.xunwu.web.controller.user.ServiceMultiResult;
import com.ximua.xunwu.web.form.RentSearch;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SearchServiceTests extends ApplicationTests {
    @Autowired
    private ISearchService searchService;

    @Test
    public void testIndex(){
        boolean success = searchService.index(15L);
        Assert.assertTrue(success);
    }

    @Test
    public void testRemove(){
        searchService.remove(15L);
    }

    @Test
    public void testQuery(){
        RentSearch rentSearch = new RentSearch();
        rentSearch.setCityEnName("bj");
        rentSearch.setStart(0);
        rentSearch.setSize(10);
        ServiceMultiResult<Long> serviceMultiResult = searchService.query(rentSearch);
        Assert.assertEquals(8, serviceMultiResult.getTotal());
    }
}
