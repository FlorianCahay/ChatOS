package fr.uge.chatos.visitor;

import fr.uge.chatos.client.Client;
import fr.uge.chatos.http.HTTPException;
import fr.uge.chatos.http.HTTPProcessor;
import fr.uge.chatos.packet.*;

import java.util.Objects;
import java.util.logging.Logger;

public class ClientPacketVisitor implements PacketVisitor {
    private static final Logger logger = Logger.getLogger(ClientPacketVisitor.class.getName());
    private final Client client;
    private final Client.Context context;

    public ClientPacketVisitor(Client client, Client.Context context) {
        this.client = Objects.requireNonNull(client);
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public void visit(Connection connection) {
        var connectionConfirmation = (ConnectionConfirmation) connection;
        if (connectionConfirmation.confirm == (byte) 1) {
            //context.successfulAuthentication();
            System.out.println("Connection success.");
        } else {
            System.out.println("Connection failed.");
        }
    }

    @Override
    public void visit(PublicMessage publicMessage) {
        System.out.println(publicMessage.sender+ " : " + publicMessage.content);
    }

    @Override
    public void visit(PrivateMessage privateMessage) {
        System.out.println("[Message privé de " + privateMessage.sender + "] : " + privateMessage.content);
    }

    @Override
    public void visit(PCRequest PCRequest) {
        PCRequest.sender = PCRequest.recipient; // vu qu'on utilise le même reader que le serveur on doit changer la valeur
        PCRequest.recipient = client.getLogin();
        var msg = "[** Demande de connexion privée reçue de la part de "+ PCRequest.sender +" **]"
                    + "\n\tPour accepter => /"+ PCRequest.sender +" oui"
                    + "\n\tPour refuser => /"+ PCRequest.sender +" non";
        System.out.println(msg);
    }

    @Override
    public void visit(PCSockets PCSockets) {
        client.startPrivateConnection(PCSockets.port, PCSockets.sender, PCSockets.id);
    }

    @Override
    public void visit(Authentication authentication) {
        var pcac = (PCAuthConfirmation) authentication;
        var pcOptional = client.getPrivateConnection(pcac.id);
        if (pcOptional.isPresent()) {
            var entry = pcOptional.get();
            entry.getValue().getContext().successfulAuthentication();
            System.out.println("Connexion privée avec "+ entry.getKey() +" établie."); // TODO : changer entry.getKey()
        }

    }

    @Override
    public void visit(PCData PCData) {}
    
    public void visit(PrivateFrame privateFrame) {
        var msg = "";
        var bb = privateFrame.asByteBuffer();
        bb.flip();
        for (var b = 0; b < 3; b++) {
          msg+=bb.get(b);
        }
        if (msg.equals("GET")) {
            var httpbb = Packets.ofHTTP(bb, privateFrame);
            context.queueMessage(httpbb);
        } else {
            bb.compact();
            try {
                HTTPProcessor.processHTTP(bb);
            } catch (HTTPException e) {
                System.out.println("HTTPException encountered with [" + privateFrame.getDst() +"]:\n"+ e.getMessage());
            }
        }
    }
}
