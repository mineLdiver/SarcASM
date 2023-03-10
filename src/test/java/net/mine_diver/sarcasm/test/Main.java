package net.mine_diver.sarcasm.test;

import net.mine_diver.sarcasm.SarcASM;

public class Main {

    public static void main(String[] args) {
        System.out.println(Target.INSTANCE.getClass().getName());
        Target.INSTANCE.test();
        SarcASM.registerInjector(Target.class, new TargetInjector());
        SarcASM.registerTransformer(Target.class, new TargetTransformer());
        System.out.println(Target.INSTANCE.getClass().getName());
        Target.INSTANCE.test();
    }
}
