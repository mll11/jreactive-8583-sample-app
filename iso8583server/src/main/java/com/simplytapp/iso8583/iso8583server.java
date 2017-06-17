package com.simplytapp.iso8583;

import com.github.kpavlov.jreactive8583.IsoMessageListener;
import com.github.kpavlov.jreactive8583.server.Iso8583Server;
import com.github.kpavlov.jreactive8583.server.ServerConfiguration;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.parse.ConfigParser;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.util.Date;

public class iso8583server {

    volatile static IsoMessage capturedRequest;

    public static void main(String[] args) {
        try {
            // 1. Create a MessageFactory.
            final MessageFactory<IsoMessage> messageFactory = ConfigParser.createDefault();
            messageFactory.setCharacterEncoding(StandardCharsets.US_ASCII.name());
            messageFactory.setUseBinaryMessages(false);
            //messageFactory.setAssignDate(true);  // Sets field 7.

            // 4. Configure the server.
            final ServerConfiguration configuration = ServerConfiguration.newBuilder()
                                                                         .withLogSensitiveData(true)
                                                                         .withReplyOnError(true)
                                                                         .build();

            // 2. Create a Iso8583Server providing port to bind to, ServerConfiguration, and MessageFactory.
            final Iso8583Server<IsoMessage> server = new Iso8583Server<>(8583, configuration, messageFactory);

            // 3. Add one or more custom IsoMessageListeners to handle IsoMessages.
            server.addMessageListener(new IsoMessageListener<IsoMessage>() {
                @Override
                public boolean applies(IsoMessage isoMessage) {
                    return isoMessage.getType() == 0x1100;
                    //return isoMessage.getType() == 0x0200;
                }

                @Override
                public boolean onMessage(ChannelHandlerContext channelHandlerContext, IsoMessage isoMessage) {
                    capturedRequest = isoMessage;
                    final IsoMessage response = server.getIsoMessageFactory().createResponse(isoMessage);
                    final IsoValue isoValueField2 = isoMessage.getField(2);
                    final String pan = (String) isoValueField2.getValue();
                    response.copyFieldsFrom(isoMessage, 2, 3, 4, 7, 11, 12, 37, 41, 42, 49);
                    response.removeFields(13, 14, 19, 22, 24, 26, 35, 45);
                    response.setField(38, IsoType.ALPHA.value("123456", 6));
                    response.setField(39, IsoType.NUMERIC.value("000", 3));
                    if (pan.startsWith("5")) {
                        // MasterCard
                        response.setField(15, IsoType.DATE6.value(new Date()));
                    }
                    channelHandlerContext.writeAndFlush(response);
                    return false;
                }
            });

            // 5. Initialize server. Now it is ready to start.
            server.init();

            // 6. Start server. Now it is ready to accept client connections.
            server.start();

            // 7. Verify that the server is started.
            if (server.isStarted()) {
                // Wait for request.
                while (capturedRequest == null) {
                    try {
                        Thread.sleep(100);
                    }
                    catch (InterruptedException e) {
                    }
                }
            }

            // 8. Shutdown server when done.
            server.shutdown();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
