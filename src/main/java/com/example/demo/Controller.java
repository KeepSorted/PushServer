package com.example.demo;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("send")
public class Controller {
    @PostMapping("/{id}")
    public ResponseEntity send(
            @PathVariable(value = "id", required = true) Long id,
            @RequestParam(value = "data", required = true) String data
    ) {
        if (!MyChannelHandlerMap.biDirectionHashMap.containsKey(id)) {
            Utils.log("该ID未注册");
            return Response.notFound();
        }
        Channel channel = MyChannelHandlerMap.biDirectionHashMap.getByKey(id);
        channel.writeAndFlush(new TextWebSocketFrame(data));
        Utils.log("向该ID发送消息:" + data);
        return Response.success();
    }
}
