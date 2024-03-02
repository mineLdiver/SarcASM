package net.mine_diver.sarcasm.test;

import java.util.Random;

public class TargetSuper {

    public final int test = new Random().nextInt();

    public void test(String testString) {
        System.out.println("Super field is " + test + ", " + testString);
    }
}
