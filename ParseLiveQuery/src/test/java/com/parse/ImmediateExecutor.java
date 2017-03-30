package com.parse;

import java.util.concurrent.Executor;

class ImmediateExecutor implements Executor {
    @Override
    public void execute(Runnable runnable) {
        runnable.run();
    }
}
