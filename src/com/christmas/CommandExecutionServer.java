package com.christmas;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public class CommandExecutionServer implements AutoCloseable {

    public static final int SERVER_PORT = 4444;
    private final int bufferSize = 1024;

    private Selector selector;
    private ByteBuffer commandBuffer;
    private ServerSocketChannel serverSocketChannel;
    private boolean runServer = true;

    public CommandExecutionServer(int port) throws IOException {
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(port));
        commandBuffer = ByteBuffer.allocate(bufferSize);
        System.out.println(InetAddress.getLocalHost().getHostAddress());
    }

    /**
     * Start the server
     *
     * @throws IOException
     */
    public void start() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (runServer) {
            int readyChannels = selector.select();
            if (readyChannels <= 0) {
                continue;
            }
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isReadable()) {
                    this.read(key);
                } else if (key.isAcceptable()) {
                    this.accept(key);
                }
                keyIterator.remove();
            }
        }
    }

    /**
     * Accept a new connection
     *
     * @param key The key for which an accept was received
     * @throws IOException In case of problems with the accept
     */
    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel channel = (ServerSocketChannel) key.channel();
        SocketChannel schannel = channel.accept();
        schannel.configureBlocking(false);
        schannel.register(selector, SelectionKey.OP_READ);
    }

    private void read(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        try {
            commandBuffer.clear();
            int r = sc.read(commandBuffer);
            if (r == -1) {
                return;
                //throw new RuntimeException("Channel is broken");
            }
            commandBuffer.flip();
            String message = Charset.forName("UTF-8").decode(commandBuffer).toString();
            String result = executeCommand(message);
            System.out.println("message:" + message);
            System.out.println("result:" + result);
            commandBuffer.clear();
            commandBuffer.put((result + System.lineSeparator()).getBytes());
            commandBuffer.flip();
            sc.write(commandBuffer);
        } catch (IOException e) {
            this.stop();
            e.getMessage();
            e.printStackTrace();
        }
    }

    /**
     * Stop the server
     *
     * @throws IOException
     */
    public void stop() throws IOException {
        runServer = false;
    }

    /**
     * Validate and execute the received commands from the clients
     *
     * @param recvMsg
     * @return The result of the execution of the command
     */
    private String executeCommand(String recvMsg) {
        String[] cmdParts = recvMsg.split(":");

        if (cmdParts.length > 2) {
            return "Incorrect command syntax";
        }

        String command = cmdParts[0].trim();

        if (command.equalsIgnoreCase("echo")) {
            if (cmdParts.length <= 1) {
                return "Missing Argument";
            }
            return cmdParts[1].trim();
        } else if (command.equalsIgnoreCase("gethostname")) {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                e.printStackTrace();
                return "Could not get hostname";
            }
        } else {
            return "Unknown command";
        }
    }

    @Override
    public void close() throws Exception {
        commandBuffer.clear();
        selector.close();
        serverSocketChannel.close();
    }

    public static void main(String args[]) throws Exception {
        try (CommandExecutionServer es = new CommandExecutionServer(SERVER_PORT)) {
            es.start();
        } catch (Exception e) {
            System.out.println("An error has occured");
            e.printStackTrace();
        }
    }
}
