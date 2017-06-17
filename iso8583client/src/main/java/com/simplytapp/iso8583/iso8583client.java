package com.simplytapp.iso8583;

import com.github.kpavlov.jreactive8583.IsoMessageListener;
import com.github.kpavlov.jreactive8583.client.ClientConfiguration;
import com.github.kpavlov.jreactive8583.client.Iso8583Client;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.parse.ConfigParser;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class iso8583client {

    volatile static IsoMessage capturedResponse;

    public static void main(String[] args) {
        try {
            // 1. Create a MessageFactory.
            final MessageFactory<IsoMessage> messageFactory = ConfigParser.createDefault();
            messageFactory.setCharacterEncoding(StandardCharsets.US_ASCII.name());
            messageFactory.setUseBinaryMessages(false);
            //messageFactory.setAssignDate(true);  // Sets field 7.

            final SocketAddress socketAddress = new InetSocketAddress("127.0.0.1", 8583);

            // 4. Configure the client.
            final ClientConfiguration configuration = ClientConfiguration.newBuilder()
                                                                         .withIdleTimeout(5)
                                                                         .withLogSensitiveData(true)
                                                                         .withReplyOnError(true)
                                                                         .build();

            // 2. Create a Iso8583Client providing SocketAddress, ClientConfiguration, and MessageFactory.
            final Iso8583Client<IsoMessage> client = new Iso8583Client<>(socketAddress, configuration, messageFactory);

            // 3. Add one or more custom IsoMessageListeners to handle IsoMessages.
            client.addMessageListener(new IsoMessageListener<IsoMessage>() {
                @Override
                public boolean applies(IsoMessage isoMessage) {
                    //return false;
                    return isoMessage.getType() == 0x1110;
                }

                @Override
                public boolean onMessage(ChannelHandlerContext channelHandlerContext, IsoMessage isoMessage) {
                    capturedResponse = isoMessage;
                    return false;
                }
            });

            // 5. Initialize client. Now it is ready to connect.
            client.init();

            // 6. Establish a connection. By default, if connection will is lost, it reconnects automatically.
            client.connect();

            // 7. Verify that connection is established.
            if (client.isConnected()) {
                final IsoMessage message = messageFactory.newMessage(0x1100);
                final String PAN = "5164123785712481";
                message.setField(2, IsoType.LLVAR.value(PAN, PAN.length()));
                message.setField(3, IsoType.NUMERIC.value("004000", 6));
                message.setField(4, IsoType.NUMERIC.value("000000000100", 12));
                message.setField(11, IsoType.ALPHA.value("100304", 6));
                message.setField(12, IsoType.DATE12.value(new Date()));
                message.setField(14, IsoType.DATE_EXP.value("1702"));
                message.setField(19, IsoType.NUMERIC.value("840", 3));
                message.setField(22, IsoType.ALPHA.value("A00101A03346", 12));
                message.setField(24, IsoType.NUMERIC.value("100", 3));
                message.setField(26, IsoType.NUMERIC.value("5814", 4));
                final String TRACK2 = PAN + "D17021011408011015360";
                message.setField(35, IsoType.LLVAR.value(TRACK2, TRACK2.length()));
                message.setField(37, IsoType.ALPHA.value("000000000411", 12));
                message.setField(41, IsoType.ALPHA.value("02001101", 8));
                message.setField(42, IsoType.ALPHA.value("502101143255555", 15));
                final String TRACK1 = "B" + PAN + "^SUPPLIED/NOT^17021011408011015360";
                message.setField(45, IsoType.LLVAR.value(TRACK1, TRACK1.length()));
                message.setField(49, IsoType.NUMERIC.value("840", 3));
                /*
                final IsoMessage message = client.getIsoMessageFactory().newMessage(0x0200);
                */

                // 8. Send IsoMessage.
                client.send(message);
            }

            // Wait for response.
            while (capturedResponse == null) {
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                }
            }

            // 9. Disconnect when done.
            client.shutdown();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
