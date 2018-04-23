package com.ximua.xunwu.service.house;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.ximua.xunwu.ApplicationTests;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

public class QiniuServiceTests extends ApplicationTests {
    @Autowired
    private IQiNiuService qiNiuService;

    @Test
    public void testUploadFile(){
        String fileName = "/Users/qiankeqin/Documents/workspace/xunwu/tmp/xiaoqian.jpeg";
        File file = new File(fileName);
        Assert.assertTrue(file.exists());
        try {
            Response response = qiNiuService.uploadFile(file);
            Assert.assertTrue(response.isOK());
        } catch (QiniuException e) {
            e.printStackTrace();

        }
    }

    @Test
    public void testDelete(){
        String key = "FhRdUkZQvlByVYno45zVT6DbDS60";
        try {
            qiNiuService.delete(key);
        } catch (QiniuException e) {
            e.printStackTrace();
        }
    }
}
