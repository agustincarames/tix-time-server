package ar.edu.itba.it.proyectofinal.tix_time.handlers;

import ar.edu.itba.it.proyectofinal.tix_time.data.TixDataPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixTimestampPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixPackage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class TixUdpClientMessageHandler extends ChannelInboundHandlerAdapter {
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		if (!(msg instanceof TixPackage)) {
			throw new IllegalArgumentException("Expected a TixPackage");
		}
		if (msg instanceof TixDataPackage) {
			System.out.println("It's data!");
		} else if (msg instanceof TixTimestampPackage) {
			System.out.println("It's a timestamp!");
		}
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		cause.printStackTrace();
	}

}
