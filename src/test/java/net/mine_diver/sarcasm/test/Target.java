package net.mine_diver.sarcasm.test;

public class Target extends TargetSuper {

    public static final Target INSTANCE = new Target();

    public Target() {}

    @Override
    public void test(String testString) {
        System.out.println("Hello world!");
        super.test(testString);
        Main.test(this);
    }

    public void testTwo() {
        System.out.println(new TargetTwo().testRecursion());
    }
}
