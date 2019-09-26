# SpringBoot + Netty + WebSocket + ConcurrentHashMap 高性能消息推送服务器

## 项目需求

最近老板提出新的需求，大概就是手机发送要打印的东西到电脑，然后电脑接收到之后打印出来。因为手机和电脑不能直接通信，所以只能通过服务器中转，核心思想是通过ID标记电脑，然后手机向该ID发送消息。

1. 能实现点对点的消息推送
2. 同时在线人数预计超过3k
3. 响应速度不超过1s

这种情况下有两种通信方案

1. POLL轮询。
   - **思路:** 服务器保存一个 ```Map<ID: Integer, Message: List<String>>```格式的消息缓存。手机发送<ID, Message>到服务器，电脑通过HTTP轮询，间隔小于1s, 通过ID获取自己的消息。
   - **优点:** 实现简单，不需要维护连接。
   - **缺点:** 响应速度慢，因为轮询有一定的间隔，不能做到实时响应；服务器开销大，因为大多数的请求都是无效的。
2. PUSH推送。
   - **思路:** 服务器维护一个连接池；电脑连接上之后服务器记录下 ```<ID, Connection>```的对应关系，手机发送<ID, Message>到服务器，服务器收到后主动向电脑推送消息。
   - **优点:** 实时性好，因为长连接没有轮询间隔，可以做到实时推送；运行开销小，因为平时只是维护连接，基本不需要消耗cpu和带宽。
   - **缺点:** 开发难度大

作为有追求的程序员[狗头]，毫无疑问选择了后者。

## 设计与技术选型

大致方向定下来了，接下来就是设计系统架构和技术选型。

## SpringBoot

之前开发用过Golang-Gin\Python-Flask，但最近Get到了Java的强大之处，所以改用Java开发。SpringBoot作为Java目前最流行的、轻量级的框架，开发这种小型应用当然再合适不过了。

### WebSocket长连接 + HTTP混合方式

1. 不难看出，只有电脑端需要维护长连接。因为手机只需要实现消息的发送，直接用HTTP发送消息就好了，也可以减少一些不必要的连接。
2. 电脑端用的Vue开发的，相较于TCP\MQTT\MQ等方案，WebSocket更加适用于Web端。

### Netty异步框架

Netty基于Nio，比传统的Bio方案效率更高，性能更好，作为有追求的程序员[再次狗头], 在没有历史包袱的情况下，当然会选用更高端的方案。

### ConcurrentHashMap

上节中提到，服务器需要维护<ID, Connection>的对应关系，这样在手机发来消息时可以找到应该推送给谁。那么我们应该用什么样的数据结构呢？

1. List。可以实现，但是每次查找的时候需要遍历列表，也就是O(n)复杂度。在连接较少的时候还可以接收，如果消息和连接数多了之后，无疑会增大系统开销，降低效率
2. HashMap。于是我们想到了HashMap，因为HashMap的插入、查找可以O(1)的复杂度。但是HashMap不是线程安全的，put操作会引起死循环。
3. HashTable。为了实现线程安全，可以使用HashTable。但其内部采用```synchronized```进行同步，是一种悲观锁，所以在多线程环境下会造成激烈竞争。
4. ConcurrentHashMap。所以最后选择了并发HashMap。ConcurrentHashMap使用了锁分段技术，将数据分段，读写时对每段加锁，这样就避免了多个线程竞争同一把锁的情况，大大提高了性能。

所以最后采用了ConcurrentHashMap保存<ID, Connection>连接，实现**O(1)的时间复杂度向连接推送消息**。

### 心跳保活、定时清理方案

WebSocket虽然有onMessage、onConnect、onClientLeft等回调，但还是会有异常的情况发生，即连接丢失，但服务器还没在全局Map中清理掉<ID, Connection>，长此以往，会造成内存泄露。所以需要清理连接的机制，保证丢失的连接可以被清理掉。

于是采用了 心跳保活+定时清理的方案，具体流程如下：

1. 电脑定时发送心跳包(时间可以稍长，10s就可以)，服务器收到后，给Connection打上最后一次收到消息的时间戳。
2. 服务器开一个线程专门检查连接是否正常，具体做法是： 比较当前时间戳和Connection的时间戳，如果间隔大于阈值(如1分钟)，则判断连接异常断开，则从Map中清理掉。

这个过程类似GC，在后台自动清理掉垃圾。由于异常失效的连接非常少，所以这个开销也基本可以忽略不计。追求极致性能还可以 使用**LinkedHashMap**，用类似LRU的方法在接近O(1)的复杂度下获得超时连接(非常少且肯定队尾)，但这会设计到同步问题(ConcurrentLinkedHashMap解决)，且有点炫技和**过度设计**的嫌疑，因此**直接暴力遍历**就完事儿了。

## 实现步骤

### 创建maven项目，导入SpringBoot、Netty、WebSocket

1. Intellij Idea用Spring Initializr创建项目，我用了Java12，因为可以一路var，爽翻，再也不用写又臭又长的类型声明了[第三次狗头]

![1569326529664](C:\Users\25101\AppData\Roaming\Typora\typora-user-images\1569326529664.png)

2. 创建Maven项目 Java version选11

   ![1569326879288](C:\Users\25101\AppData\Roaming\Typora\typora-user-images\1569326879288.png)

3. 一路next

4. maven引入 netty、SprintBoot (pom.xml)

   ```
   <dependency>
       <groupId>io.netty</groupId>
       <artifactId>netty-all</artifactId>
       <version>4.1.36.Final</version>
   </dependency>
   <dependency>
    <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-web</artifactId>
   </dependency>
   
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-test</artifactId>
       <scope>test</scope>
   </dependency>
   ```

### 并发安全获取自增ID  (Utils.java)

```java
 public class Utils {
    private static AtomicInteger counter = new AtomicInteger(0);
    /**
     * 生成ID, time: 13位 + random: 3位
     * @return id
     */
    public static long generateID () {
        return 1000000000 + counter.getAndIncrement();
    }

    /**
     * 打印
     * @param s
     */
    public static void log(String s) {
	//  System.out.println("[" + new Date().toString() + "]  " + s);
    }
}

```

这个generateID函数是为了给客户端分配唯一ID设计的。因为在高并发情况下，普通int型数据无法保证并发安全

### 处理连接，转发消息

首先是能通过键或值，在O(1) 的复杂度下进行 插入、查找、删除的 "双向HashMap"  (BiDirectionHashMap.java)

```java
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双向HashMap， 可以实现O(1) 按值/键 查找、添加、删除元素对
 */
public class BiDirectionHashMap<K, V> {
    private ConcurrentHashMap<K, V> k2v; // key -> value
    private ConcurrentHashMap<V, K> v2k; // value -> key

    /**
     * 默认构造函数
     */
    BiDirectionHashMap() {
        this.k2v = new ConcurrentHashMap<>();
        this.v2k = new ConcurrentHashMap<>();
    }

    /**
     * 添加
     * @param k 键
     * @param v 值
     */
    public void put(K k, V v) {
        k2v.put(k, v);
        v2k.put(v, k);
    }

    /**
     * 查看大小
     * @return 大小
     */
    public int size () {
        return k2v.size();
    }

    /**
     * 是否有键
     * @param k 键
     * @return
     */
    public boolean containsKey(K k) {
        return k2v.containsKey(k);
    }

    /**
     * 是否有Value
     * @param v 值
     * @return
     */
    public boolean containsValue(V v) {
        return v2k.containsKey(v);
    }

    /**
     * 通过键删除
     * @param k 键
     * @return
     */
    public boolean removeByKey(K k) {
        if (!k2v.containsKey(k)) {
            return false;
        }

        V value = k2v.get(k);
        k2v.remove(k);
        v2k.remove(value);
        return true;
    }

    /**
     * 通过值删除
     * @param v 值
     * @return
     */
    public boolean removeByValue(V v) {
        if (!v2k.containsKey(v)) {
            return false;
        }

        K key = v2k.get(v);
        v2k.remove(v);
        k2v.remove(key);
        return true;
    }

    /**
     * 通过键获取值
     * @param k
     * @return
     */
    public V getByKey(K k) {
        return k2v.getOrDefault(k, null);
    }

    /**
     * 通过值获取键
     * @param v
     * @return
     */
    public K getByValue(V v) {
        return v2k.getOrDefault(v, null);
    }
}
```

其中用到了两个ConcurrentHashMap，用到了分段锁保证并发安全。

接下来是用来记录连接的类(MyChannelHandlerMap.java)

```java
/**
 * 用于共享
 */
public class MyChannelHandlerMap {
    /**
     * 保存映射关系的双向Hash表
     */
    public static BiDirectionHashMap<Long, Channel> biDirectionHashMap = new BiDirectionHashMap<>();

    /**
     * TODO: 不活跃连接/异常连接清除
     * 记录最后一次通信时间, 用于确定不活跃连接，然后清理掉
     */
    public static ConcurrentHashMap<Long, Date> lastUpdate = new ConcurrentHashMap<>();

    /**
     * 是否存在连接
     * @param id
     * @return
     */
    public boolean existConnectionByID (Long id) {
        return biDirectionHashMap.containsKey(id);
    }
}
```

然后开始启动Netty服务器 (NettyServer.java)，对Netty服务器进行配置，接收WebSocket请求，并交由Handler处理

```java
public class NettyServer {
    private final int port;

    NettyServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.option(ChannelOption.SO_BACKLOG, 4096);
            sb.group(group, bossGroup) // 绑定线程池
                    .channel(NioServerSocketChannel.class) // 指定使用的channel
                    .localAddress(this.port)// 绑定监听端口
                    .childHandler(new ChannelInitializer<SocketChannel>() { // 绑定客户端连接时候触发操作
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            Utils.log("收到新连接");
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new ChunkedWriteHandler());
                            ch.pipeline().addLast(new HttpObjectAggregator(8192));
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler("/push", null, true, 65536 * 10));
                            ch.pipeline().addLast(new MyWebSocketHandler());
                        }
                    });
            ChannelFuture cf = sb.bind().sync(); // 服务器异步创建绑定
            Utils.log(NettyServer.class + " 启动正在监听： " + cf.channel().localAddress());
            cf.channel().closeFuture().sync(); // 关闭服务器通道
        } finally {
            group.shutdownGracefully().sync(); // 释放线程池资源
            bossGroup.shutdownGracefully().sync();
        }
    }
}
```

接下来是处理消息的Handler (MyWebSocketHandler.java)

```java
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
```

和处理http消息的Controler.java

```java
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

```

Response.java

```java
public class Response {
    public static ResponseEntity success() {
        return new ResponseEntity<>((Map<String, Object>) null, HttpStatus.OK);
    }

    public static ResponseEntity notFound() {
        return new ResponseEntity<>((Map<String, Object>) null, HttpStatus.NOT_FOUND);
    }

    public static ResponseEntity error() {
        return new ResponseEntity<>((Map<String, Object>) null, HttpStatus.BAD_REQUEST);
    }
}
```

PushServiceApplication.java 入口

```java
@SpringBootApplication
public class PushServerApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(PushServerApplication.class, args);

        new Thread(new ClientsCheck()).start();  // 客户端检查

        try {
            new NettyServer(12345).start();
        }catch(Exception e) {
            Utils.log("NettyServerError:"+e.getMessage());
        }

    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder springApplicationBuilder) {
        return springApplicationBuilder.sources(this.getClass());
    }

}
```

流程大概是这样的：

1. Client与Server建立连接，此时不做任何处理
2. Client连接成功后，发送 **getID**指令
3. Server收到**getID**指令后，为该客户端生成一个唯一ID，并将<ID, Channel>的映射关系存起来
4. 其他客户端(推送方)发送消息<ID, Message>的时候，Server查Hash表找到Channel，并向其发送Message
5. 当Client断开之后，Server通过Channel快速找到ID，并在双向Hash表中删除两者

### 定时清理

ClientsCheck.java (待完善)

```java
public class ClientsCheck implements Runnable{
    @Override
    public void run() {
        try {
            while (true) {
                int size = MyChannelHandlerMap.biDirectionHashMap.size();
                Utils.log("client quantity -> " + size);
                Thread.sleep(10000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

这个类实现了Runnable接口，可以作为一个后台任务清理不活动的连接，前面我们有记录每个连接的最后通信时间```lastUpdated```，我么可以将当前时间```now```与```lastUpdated```进行比较，超过阈值则清理连接。

## 性能测试

使用Python的gevent进行大并发测试。

### 10k并发测试

```python
from gevent import monkey; monkey.patch_all()
import gevent
import websocket
from gevent import pool

PUSH_URL = 'ws://xxx.xxx.xxx/push'  # ws的url

def create_ws():
    ws = websocket.WebSocketApp(PUSH_URL, 
                                on_open=lambda ws: ws.send('getID'),  # 连接后发送getID指令
                                on_message=lambda ws, msg: print(msg),
                                on_error=lambda ws, err: print(err))
    ws.run_forever()

threads = []
for i in range(10000):  # 并发10000
    threads.append(gevent.spawn(create_ws))

print('finished -> ', len(threads))
gevent.joinall(threads)
```

在测试程序里面，通过gevent并发了10000个连接 (基于协程的并发框架，如果线程的话做不到这么高)



在并发数为10k的时候，后台占用400多MB内存，在可接受的范围内，并且在维持连接的时候，几乎不占用CPU资源

### 单次延迟 (非严谨)

使用curl工具调用发送接口，并记录当前时间。测试环境为

- CPU: AMD Ryzen 3500u
- 内存: 12g

用本地环回地址 127.0.0.1进行单机测试


单次消息响应时间应当小于200ms

### 平均延迟 (待续)

待续...

