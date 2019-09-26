package com.example.demo;

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
