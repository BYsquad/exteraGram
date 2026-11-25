package org.telegram.messenger;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.atomic.AtomicInteger;

public final class BygramProfileViewsJob extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        ApplicationLoader.postInitApplication();
        AtomicInteger remaining = new AtomicInteger(UserConfig.MAX_ACCOUNT_COUNT);
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            BygramAccountService.pollProfileViews(account, () -> {
                if (remaining.decrementAndGet() == 0) jobFinished(params, false);
            });
        }
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
