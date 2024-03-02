package net.mine_diver.sarcasm.test;

import net.mine_diver.sarcasm.SarcASM;

public class Main {

    public static void main(String[] args) {
        System.out.println(Target.INSTANCE.getClass().getName());
        Target.INSTANCE.test("any string");
        SarcASM.registerInjector(Target.class, new TargetInjector());
        SarcASM.getManager(Target.class).register(new TargetTransformer());
        System.out.println(Target.INSTANCE.getClass().getName());
        Target.INSTANCE.test("any string");
        Target untracked = SarcASM.newUntrackedProxy(Target::new);
        System.out.println(untracked.getClass().getName());
        System.out.println(untracked == Target.INSTANCE);
        untracked.test("any string");
        SarcASM.getManager(TargetTwo.class).register(new TargetTwoTransformer());
        untracked.testTwo();
    }

    public static void test(Target target) {
        System.out.println(target);
    }
}
