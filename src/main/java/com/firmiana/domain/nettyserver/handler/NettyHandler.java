package com.firmiana.domain.nettyserver.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.firmiana.domain.nettyserver.enums.RouteEnum;
import com.firmiana.domain.nettyserver.model.vo.SendMessageVO;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @date 2021-07-16
 * @author oujunting
 */
@Service
@Slf4j
public class NettyHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    //TextWebSocketFrame是netty用于处理websocket发来的文本对象

    //所有正在连接的channel都会存在这里面，所以也可以间接代表在线的客户端
    public static ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    //在线人数
    public static int online;
    //url过滤
    private static String END_POINT = "/ws";

    @Value("${netty.endpoint}")
    private String endpoint;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof PingWebSocketFrame) {
            pingWebSocketFrameHandler(ctx, (PingWebSocketFrame) frame);
        } else if (frame instanceof TextWebSocketFrame) {
            textWebSocketFrameHandler(ctx, (TextWebSocketFrame) frame);
        } else if (frame instanceof CloseWebSocketFrame) {
            closeWebSocketFrameHandler(ctx, (CloseWebSocketFrame) frame);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.info("数据类型：{}", msg.getClass());
        if (msg instanceof FullHttpRequest) {
            fullHttpRequestHandler(ctx, (FullHttpRequest) msg);
        }
        super.channelRead(ctx, msg);
    }

    /**
     * 处理连接请求，客户端WebSocket发送握手包时会执行这一次请求
     *
     * @param ctx
     * @param request
     */
    private void fullHttpRequestHandler(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        log.debug("接收到客户端的握手包：{}", ctx.channel().id());
        if (uri.endsWith(END_POINT)){
            request.setUri(END_POINT);
        }
        else{
            if(request.method() == HttpMethod.GET){
                Map<String, Object> params = getGetParamsFromChannel(request);
            }else if(request.method() == HttpMethod.POST){
                Map<String, Object> body = getPostParamsFromChannel(request);
                if(RouteEnum.SEND_ALL_MESSAGE.getPath().equals(uri)){
                    SendMessageVO messageVO = new SendMessageVO();
                    messageVO.setTitle(Objects.isNull(body.get("title"))?null:body.get("title").toString());
                    messageVO.setContent(Objects.isNull(body.get("content"))?null:body.get("content").toString());
                    sendAllMessages(ctx, messageVO);
                }
            }
            ctx.close();
        }
    }


    private Map<String, Object> getGetParamsFromChannel(FullHttpRequest fullHttpRequest) {
        Map<String, Object> params = new HashMap<>();
        if(fullHttpRequest.method() == HttpMethod.GET){
            QueryStringDecoder decoder = new QueryStringDecoder(fullHttpRequest.uri());
            Map<String, List<String>> paramList = decoder.parameters();

            for(Map.Entry<String, List<String>> entry : paramList.entrySet()){
                params.put(entry.getKey(),entry.getValue().get(0));
            }
            return params;
        }
        return params;
    }

    private Map<String, Object> getPostParamsFromChannel(FullHttpRequest fullHttpRequest) {
        Map<String, Object> params = new HashMap<>();
        if(fullHttpRequest.method() == HttpMethod.POST){
            // 处理post 请求
            String strContentType = fullHttpRequest.headers().get("Content-Type").trim();
            if(StringUtil.isNullOrEmpty(strContentType)){
                return null;
            }
            if(strContentType.contains("x-www-form-urlencoded")){
                params = getFormParams(fullHttpRequest);
            }else if(strContentType.contains("application/json")){
                params = getJSONParams(fullHttpRequest);
            }else {
                return null;
            }
        }
        return params;
    }

    private Map<String, Object> getJSONParams(FullHttpRequest fullHttpRequest) {
        Map<String, Object> params = new HashMap<>();

        ByteBuf content = fullHttpRequest.content();
        byte[] reqContent = new byte[content.readableBytes()];
        content.readBytes(reqContent);
        String strContent = null;
        try {
            strContent = new String(reqContent, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        JSONObject jsonParams = JSONObject.parseObject(strContent);
        for (Object key : jsonParams.keySet()) {
            params.put(key.toString(), jsonParams.get(key));
        }

        return params;

    }


    private Map<String, Object> getFormParams(FullHttpRequest fullHttpRequest) {
        Map<String, Object> params = new HashMap<>();

        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), fullHttpRequest);
        List<InterfaceHttpData> postData = decoder.getBodyHttpDatas();

        for (InterfaceHttpData data : postData) {
            if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                MemoryAttribute attribute = (MemoryAttribute) data;
                params.put(attribute.getName(), attribute.getValue());
            }
        }

        return params;
    }


    /**
     * 处理客户端心跳包
     *
     * @param ctx
     * @param frame
     */
    private void pingWebSocketFrameHandler(ChannelHandlerContext ctx, PingWebSocketFrame frame) {
        ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
    }

    /**
     * 创建连接之后，客户端发送的消息都会在这里处理
     *
     * @param ctx
     * @param frame
     */
    private void textWebSocketFrameHandler(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();
        log.debug("接收到客户端的消息：{}", text);
        // 将客户端消息回送给客户端
        ctx.channel().writeAndFlush(new TextWebSocketFrame("你发送的内容是：" + text));
    }


    /**
     * 客户端发送断开请求处理
     *
     * @param ctx
     * @param frame
     */
    private void closeWebSocketFrameHandler(ChannelHandlerContext ctx, CloseWebSocketFrame frame) {
        log.debug("接收到主动断开请求：{}", ctx.channel().id());
        ctx.close();
    }

    //客户端建立连接
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

        channelGroup.add(ctx.channel());
        online=channelGroup.size();
        System.out.println(ctx.channel().remoteAddress()+"上线了!当前连接数为" + online);
    }

    //关闭连接
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        channelGroup.remove(ctx.channel());
        online=channelGroup.size();
        System.out.println(ctx.channel().remoteAddress()+"断开连接,当前连接数为" + online);
    }

    //出现异常
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }

    //给某个人发送消息
    private void sendMessage(ChannelHandlerContext ctx, SendMessageVO msg) {
        ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(msg)));
    }

    //给每个人发送消息,除发消息人外
    private void sendAllMessages(ChannelHandlerContext ctx,SendMessageVO msg) {
        for(Channel channel:channelGroup){
            if(!channel.id().equals(ctx.channel().id())){
                channel.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(msg)));
            }
        }
    }
}
