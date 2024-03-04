package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Test
    public void testLogical(){
        shopService.saveShop2Redis(3L, 60L);
    }

    private final ExecutorService es = Executors.newFixedThreadPool(300);

    @Test
    public void testIdWorker() throws InterruptedException {
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
        };
        for (int i = 0; i < 100; i++) {
            es.submit(task);
        }
        // 防止主线程结束太快关闭redis连接导致redis任务中断
        Thread.sleep(10 * 1000);
    }

}
