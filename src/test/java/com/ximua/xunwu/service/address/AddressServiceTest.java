package com.ximua.xunwu.service.address;

import com.ximua.xunwu.ApplicationTests;
import com.ximua.xunwu.service.ServiceResult;
import com.ximua.xunwu.service.house.IAddressService;
import com.ximua.xunwu.service.search.BaiduMapLocation;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.AssertTrue;

public class AddressServiceTest extends ApplicationTests {
    @Autowired
    private IAddressService addressService;

    //测试通过
    @Test
    public void testGetMapLocation(){
        String city = "北京";
        String address = "北京市昌平区巩华家园1号楼2单元";
        ServiceResult<BaiduMapLocation> location = addressService.getBaiduMapLocation(city, address);
        Assert.assertTrue(location.isSuccess());
        Assert.assertTrue(location.getResult().getLongitude()>0);
        Assert.assertTrue(location.getResult().getLatitude()>0);

    }
}
