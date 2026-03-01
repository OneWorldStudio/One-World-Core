package net.minecraft.network;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.LazyLoadedValue;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class Connection extends SimpleChannelInboundHandler<Packet<?>> {
   private static final float f_178299_ = 0.75F;
   private static final Logger f_129465_ = LogUtils.getLogger();
   public static final Marker f_129459_ = MarkerFactory.getMarker("NETWORK");
   public static final Marker f_129460_ = Util.m_137469_(MarkerFactory.getMarker("NETWORK_PACKETS"), (p_202569_) -> {
      p_202569_.add(f_129459_);
   });
   public static final Marker f_202554_ = Util.m_137469_(MarkerFactory.getMarker("PACKET_RECEIVED"), (p_202562_) -> {
      p_202562_.add(f_129460_);
   });
   public static final Marker f_202555_ = Util.m_137469_(MarkerFactory.getMarker("PACKET_SENT"), (p_202557_) -> {
      p_202557_.add(f_129460_);
   });
   public static final AttributeKey<ConnectionProtocol> f_129461_ = AttributeKey.valueOf("protocol");
   public static final LazyLoadedValue<NioEventLoopGroup> f_129462_ = new LazyLoadedValue<>(() -> {
      return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).build());
   });
   public static final LazyLoadedValue<EpollEventLoopGroup> f_129463_ = new LazyLoadedValue<>(() -> {
      return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).build());
   });
   public static final LazyLoadedValue<DefaultEventLoopGroup> f_129464_ = new LazyLoadedValue<>(() -> {
      return new DefaultEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Local Client IO #%d").setDaemon(true).build());
   });
   private final PacketFlow f_129466_;
   private final Queue<Connection.PacketHolder> f_129467_ = Queues.newConcurrentLinkedQueue();
   public Channel f_129468_;
   public SocketAddress f_129469_;
   private PacketListener f_129470_;
   private Component f_129471_;
   private boolean f_129472_;
   private boolean f_129473_;
   private int f_129474_;
   private int f_129475_;
   private float f_129476_;
   private float f_129477_;
   private int f_129478_;
   private boolean f_129479_;
   @Nullable
   private volatile Component f_290021_;

   public Connection(PacketFlow p_129482_) {
      this.f_129466_ = p_129482_;
   }

   public void channelActive(ChannelHandlerContext p_129525_) throws Exception {
      super.channelActive(p_129525_);
      this.f_129468_ = p_129525_.channel();
      this.f_129469_ = this.f_129468_.remoteAddress();

      try {
         this.m_129498_(ConnectionProtocol.HANDSHAKING);
      } catch (Throwable throwable) {
         f_129465_.error(LogUtils.FATAL_MARKER, "Failed to change protocol to handshake", throwable);
      }

      if (this.f_290021_ != null) {
         this.m_129507_(this.f_290021_);
      }

   }

   public void m_129498_(ConnectionProtocol p_129499_) {
      this.f_129468_.attr(f_129461_).set(p_129499_);
      this.f_129468_.attr(BundlerInfo.f_263730_).set(p_129499_);
      this.f_129468_.config().setAutoRead(true);
      f_129465_.debug("Enabled auto read");
   }

   public void channelInactive(ChannelHandlerContext p_129527_) {
      this.m_129507_(Component.m_237115_("disconnect.endOfStream"));
   }

   public void exceptionCaught(ChannelHandlerContext p_129533_, Throwable p_129534_) {
      if (p_129534_ instanceof SkipPacketException) {
         f_129465_.debug("Skipping packet due to errors", p_129534_.getCause());
      } else {
         boolean flag = !this.f_129479_;
         this.f_129479_ = true;
         if (this.f_129468_.isOpen()) {
            if (p_129534_ instanceof TimeoutException) {
               f_129465_.debug("Timeout", p_129534_);
               this.m_129507_(Component.m_237115_("disconnect.timeout"));
            } else {
               Component component = Component.m_237110_("disconnect.genericReason", "Internal Exception: " + p_129534_);
               if (flag) {
                  f_129465_.debug("Failed to sent packet", p_129534_);
                  ConnectionProtocol connectionprotocol = this.m_178315_();
                  Packet<?> packet = (Packet<?>)(connectionprotocol == ConnectionProtocol.LOGIN ? new ClientboundLoginDisconnectPacket(component) : new ClientboundDisconnectPacket(component));
                  this.m_243124_(packet, PacketSendListener.m_243092_(() -> {
                     this.m_129507_(component);
                  }));
                  this.m_129540_();
               } else {
                  f_129465_.debug("Double fault", p_129534_);
                  this.m_129507_(component);
               }
            }

         }
      }
   }

   protected void channelRead0(ChannelHandlerContext p_129487_, Packet<?> p_129488_) {
      if (this.f_129468_.isOpen()) {
         try {
            m_129517_(p_129488_, this.f_129470_);
         } catch (RunningOnDifferentThreadException runningondifferentthreadexception) {
         } catch (RejectedExecutionException rejectedexecutionexception) {
            this.m_129507_(Component.m_237115_("multiplayer.disconnect.server_shutdown"));
         } catch (ClassCastException classcastexception) {
            f_129465_.error("Received {} that couldn't be processed", p_129488_.getClass(), classcastexception);
            this.m_129507_(Component.m_237115_("multiplayer.disconnect.invalid_packet"));
         }

         ++this.f_129474_;
      }

   }

   private static <T extends PacketListener> void m_129517_(Packet<T> p_129518_, PacketListener p_129519_) {
      p_129518_.m_5797_((T)p_129519_);
   }

   public void m_129505_(PacketListener p_129506_) {
      Validate.notNull(p_129506_, "packetListener");
      this.f_129470_ = p_129506_;
   }

   public void m_129512_(Packet<?> p_129513_) {
      this.m_243124_(p_129513_, (PacketSendListener)null);
   }

   public void m_243124_(Packet<?> p_243248_, @Nullable PacketSendListener p_243316_) {
      if (this.m_129536_()) {
         this.m_129544_();
         this.m_129520_(p_243248_, p_243316_);
      } else {
         this.f_129467_.add(new Connection.PacketHolder(p_243248_, p_243316_));
      }

   }

   private void m_129520_(Packet<?> p_129521_, @Nullable PacketSendListener p_243246_) {
      ConnectionProtocol connectionprotocol = ConnectionProtocol.m_129592_(p_129521_);
      ConnectionProtocol connectionprotocol1 = this.m_178315_();
      ++this.f_129475_;
      if (connectionprotocol1 != connectionprotocol) {
         if (connectionprotocol == null) {
            throw new IllegalStateException("Encountered packet without set protocol: " + p_129521_);
         }

         f_129465_.debug("Disabled auto read");
         this.f_129468_.config().setAutoRead(false);
      }

      if (this.f_129468_.eventLoop().inEventLoop()) {
         this.m_243087_(p_129521_, p_243246_, connectionprotocol, connectionprotocol1);
      } else {
         this.f_129468_.eventLoop().execute(() -> {
            this.m_243087_(p_129521_, p_243246_, connectionprotocol, connectionprotocol1);
         });
      }

   }

   private void m_243087_(Packet<?> p_243260_, @Nullable PacketSendListener p_243290_, ConnectionProtocol p_243203_, ConnectionProtocol p_243307_) {
      if (p_243203_ != p_243307_) {
         this.m_129498_(p_243203_);
      }

      ChannelFuture channelfuture = this.f_129468_.writeAndFlush(p_243260_);
      if (p_243290_ != null) {
         channelfuture.addListener((p_243167_) -> {
            if (p_243167_.isSuccess()) {
               p_243290_.m_243096_();
            } else {
               Packet<?> packet = p_243290_.m_243103_();
               if (packet != null) {
                  ChannelFuture channelfuture1 = this.f_129468_.writeAndFlush(packet);
                  channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
               }
            }

         });
      }

      channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
   }

   private ConnectionProtocol m_178315_() {
      return this.f_129468_.attr(f_129461_).get();
   }

   private void m_129544_() {
      if (this.f_129468_ != null && this.f_129468_.isOpen()) {
         synchronized(this.f_129467_) {
            Connection.PacketHolder connection$packetholder;
            while((connection$packetholder = this.f_129467_.poll()) != null) {
               this.m_129520_(connection$packetholder.f_129558_, connection$packetholder.f_129559_);
            }

         }
      }
   }

   public void m_129483_() {
      this.m_129544_();
      PacketListener packetlistener = this.f_129470_;
      if (packetlistener instanceof TickablePacketListener tickablepacketlistener) {
         tickablepacketlistener.m_9933_();
      }

      if (!this.m_129536_() && !this.f_129473_) {
         this.m_129541_();
      }

      if (this.f_129468_ != null) {
         this.f_129468_.flush();
      }

      if (this.f_129478_++ % 20 == 0) {
         this.m_7073_();
      }

   }

   protected void m_7073_() {
      this.f_129477_ = Mth.m_14179_(0.75F, (float)this.f_129475_, this.f_129477_);
      this.f_129476_ = Mth.m_14179_(0.75F, (float)this.f_129474_, this.f_129476_);
      this.f_129475_ = 0;
      this.f_129474_ = 0;
   }

   public SocketAddress m_129523_() {
      return this.f_129469_;
   }

   public void m_129507_(Component p_129508_) {
      if (this.f_129468_ == null) {
         this.f_290021_ = p_129508_;
      }

      if (this.m_129536_()) {
         this.f_129468_.close().awaitUninterruptibly();
         this.f_129471_ = p_129508_;
      }

   }

   public boolean m_129531_() {
      return this.f_129468_ instanceof LocalChannel || this.f_129468_ instanceof LocalServerChannel;
   }

   public PacketFlow m_178313_() {
      return this.f_129466_;
   }

   public PacketFlow m_178314_() {
      return this.f_129466_.m_178539_();
   }

   public static Connection m_178300_(InetSocketAddress p_178301_, boolean p_178302_) {
      Connection connection = new Connection(PacketFlow.CLIENTBOUND);
      ChannelFuture channelfuture = m_290025_(p_178301_, p_178302_, connection);
      channelfuture.syncUninterruptibly();
      return connection;
   }

   public static ChannelFuture m_290025_(InetSocketAddress p_290034_, boolean p_290035_, final Connection p_290031_) {
      Class<? extends SocketChannel> oclass;
      LazyLoadedValue<? extends EventLoopGroup> lazyloadedvalue;
      if (Epoll.isAvailable() && p_290035_) {
         oclass = EpollSocketChannel.class;
         lazyloadedvalue = f_129463_;
      } else {
         oclass = NioSocketChannel.class;
         lazyloadedvalue = f_129462_;
      }

      return (new Bootstrap()).group(lazyloadedvalue.m_13971_()).handler(new ChannelInitializer<Channel>() {
         protected void initChannel(Channel p_129552_) {
            try {
               p_129552_.config().setOption(ChannelOption.TCP_NODELAY, true);
            } catch (ChannelException channelexception) {
            }

            ChannelPipeline channelpipeline = p_129552_.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
            Connection.m_264299_(channelpipeline, PacketFlow.CLIENTBOUND);
            channelpipeline.addLast("packet_handler", p_290031_);
         }
      }).channel(oclass).connect(p_290034_.getAddress(), p_290034_.getPort());
   }

   public static void m_264299_(ChannelPipeline p_265436_, PacketFlow p_265104_) {
      PacketFlow packetflow = p_265104_.m_178539_();
      p_265436_.addLast("splitter", new Varint21FrameDecoder()).addLast("decoder", new PacketDecoder(p_265104_)).addLast("prepender", new Varint21LengthFieldPrepender()).addLast("encoder", new PacketEncoder(packetflow)).addLast("unbundler", new PacketBundleUnpacker(packetflow)).addLast("bundler", new PacketBundlePacker(p_265104_));
   }

   public static Connection m_129493_(SocketAddress p_129494_) {
      final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
      (new Bootstrap()).group(f_129464_.m_13971_()).handler(new ChannelInitializer<Channel>() {
         protected void initChannel(Channel p_129557_) {
            ChannelPipeline channelpipeline = p_129557_.pipeline();
            channelpipeline.addLast("packet_handler", connection);
         }
      }).channel(LocalChannel.class).connect(p_129494_).syncUninterruptibly();
      return connection;
   }

   public void m_129495_(Cipher p_129496_, Cipher p_129497_) {
      this.f_129472_ = true;
      this.f_129468_.pipeline().addBefore("splitter", "decrypt", new CipherDecoder(p_129496_));
      this.f_129468_.pipeline().addBefore("prepender", "encrypt", new CipherEncoder(p_129497_));
   }

   public boolean m_129535_() {
      return this.f_129472_;
   }

   public boolean m_129536_() {
      return this.f_129468_ != null && this.f_129468_.isOpen();
   }

   public boolean m_129537_() {
      return this.f_129468_ == null;
   }

   public PacketListener m_129538_() {
      return this.f_129470_;
   }

   @Nullable
   public Component m_129539_() {
      return this.f_129471_;
   }

   public void m_129540_() {
      if (this.f_129468_ != null) {
         this.f_129468_.config().setAutoRead(false);
      }

   }

   public void m_129484_(int p_129485_, boolean p_182682_) {
      if (p_129485_ >= 0) {
         if (this.f_129468_.pipeline().get("decompress") instanceof CompressionDecoder) {
            ((CompressionDecoder)this.f_129468_.pipeline().get("decompress")).m_182677_(p_129485_, p_182682_);
         } else {
            this.f_129468_.pipeline().addBefore("decoder", "decompress", new CompressionDecoder(p_129485_, p_182682_));
         }

         if (this.f_129468_.pipeline().get("compress") instanceof CompressionEncoder) {
            ((CompressionEncoder)this.f_129468_.pipeline().get("compress")).m_129449_(p_129485_);
         } else {
            this.f_129468_.pipeline().addBefore("encoder", "compress", new CompressionEncoder(p_129485_));
         }
      } else {
         if (this.f_129468_.pipeline().get("decompress") instanceof CompressionDecoder) {
            this.f_129468_.pipeline().remove("decompress");
         }

         if (this.f_129468_.pipeline().get("compress") instanceof CompressionEncoder) {
            this.f_129468_.pipeline().remove("compress");
         }
      }

   }

   public void m_129541_() {
      if (this.f_129468_ != null && !this.f_129468_.isOpen()) {
         if (this.f_129473_) {
            f_129465_.warn("handleDisconnection() called twice");
         } else {
            this.f_129473_ = true;
            if (this.m_129539_() != null) {
               this.m_129538_().m_7026_(this.m_129539_());
            } else if (this.m_129538_() != null) {
               this.m_129538_().m_7026_(Component.m_237115_("multiplayer.disconnect.generic"));
            }
         }

      }
   }

   public float m_129542_() {
      return this.f_129476_;
   }

   public float m_129543_() {
      return this.f_129477_;
   }

   static class PacketHolder {
      final Packet<?> f_129558_;
      @Nullable
      final PacketSendListener f_129559_;

      public PacketHolder(Packet<?> p_243302_, @Nullable PacketSendListener p_243266_) {
         this.f_129558_ = p_243302_;
         this.f_129559_ = p_243266_;
      }
   }
}