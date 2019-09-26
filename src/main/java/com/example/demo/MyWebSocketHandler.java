package com.example.demo;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.Date;

public class MyWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Utils.log("与客户端建立连接，通道开启！");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (!MyChannelHandlerMap.biDirectionHashMap.containsValue(channel)) {
            Utils.log("该客户端未注册");
            return;
        }
        MyChannelHandlerMap.biDirectionHashMap.removeByValue(channel);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    /**
     * 刷新最后一次通信时间
     * @param channel 通道
     */
    private void freshTime (Channel channel) {
        if (MyChannelHandlerMap.biDirectionHashMap.containsValue(channel)) {
            Utils.log("update time");
            long id = MyChannelHandlerMap.biDirectionHashMap.getByValue(channel);
            MyChannelHandlerMap.lastUpdate.put(id, new Date());
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) throws Exception {
        Channel channel = ctx.channel();
        freshTime(channel);

        Utils.log("read0: " + textWebSocketFrame.text());
        String text = textWebSocketFrame.text();

        // 收到生成ID的指令, 返回 id:xxxxxxxx
        if (text.equals("getID")) {
            // 已建立连接, 则返回已有ID
            if (MyChannelHandlerMap.biDirectionHashMap.containsValue(channel)) {
                Long id = MyChannelHandlerMap.biDirectionHashMap.getByValue(channel);
                channel.writeAndFlush(new TextWebSocketFrame("id:" + id));
                return;
            }
            Long id = Utils.generateID();  // 创建ID
            Utils.log("id ->  " + id);
            channel.writeAndFlush(new TextWebSocketFrame("id:" + id));
            MyChannelHandlerMap.biDirectionHashMap.put(id, ctx.channel());
            MyChannelHandlerMap.lastUpdate.put(id, new Date());
        }
    }
}
