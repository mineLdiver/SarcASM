package net.mine_diver.sarcasm.test;

public class Target {

    public static final Target INSTANCE = new Target();

    private Target() {}

    public void test() {
        System.out.println("Hello world!");
    }
}
