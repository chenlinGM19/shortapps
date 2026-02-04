package com.shortapps.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.List;

public class NotificationActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("EXECUTE_ITEM".equals(intent.getAction())) {
            String id = intent.getStringExtra("item_id");
            List<DataModel.WindowConfig> windows = DataManager.loadWindows(context);
            
            for(DataModel.WindowConfig w : windows) {
                for(DataModel.ShortcutItem item : w.items) {
                    if(item.id.equals(id)) {
                        execute(context, item);
                        return;
                    }
                }
            }
        }
    }
    
    private void execute(Context context, DataModel.ShortcutItem item) {
        if (item.type == DataModel.ShortcutItem.Type.APP) {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(item.packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
            }
        } else if (item.type == DataModel.ShortcutItem.Type.TASKER) {
            Intent tIntent = new Intent("net.dinglisch.android.tasker.ACTION_TASK");
            tIntent.putExtra("task_name", item.taskName);
            context.sendBroadcast(tIntent);
        }
    }
}