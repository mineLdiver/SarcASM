package net.mine_diver.sarcasm.test;

public class Target extends TargetSuper {

    public static final Target INSTANCE = new Target();

    public Target() {}

    @Override
    public void test() {
        System.out.println("Hello world!");
        super.test();
    }

    public void testTwo() {
        System.out.println(new TargetTwo().testRecursion());
    }
}
