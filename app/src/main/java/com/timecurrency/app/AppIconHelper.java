package com.timecurrency.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

public class AppIconHelper {

    public static void setIcon(Context context, String aliasName) {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();

        // List of all aliases including the default activity
        String[] aliases = {
            ".MainActivity",
            ".MainActivityAlias1",
            ".MainActivityAlias2"
        };

        for (String alias : aliases) {
            int state = alias.equals(aliasName) ? 
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED : 
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            
            pm.setComponentEnabledSetting(
                new ComponentName(packageName, packageName + alias),
                state,
                PackageManager.DONT_KILL_APP
            );
        }
    }
    
    public static String getCurrentAlias(Context context) {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        
        String[] aliases = {
            ".MainActivityAlias1",
            ".MainActivityAlias2"
        };
        
        for (String alias : aliases) {
            if (pm.getComponentEnabledSetting(new ComponentName(packageName, packageName + alias)) 
                    == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return alias;
            }
        }
        return ".MainActivity";
    }

    public static int getCurrentIconResource(Context context) {
        String alias = getCurrentAlias(context);
        if (".MainActivityAlias1".equals(alias)) {
            return R.drawable.myicon1;
        } else if (".MainActivityAlias2".equals(alias)) {
            return R.drawable.myicon2;
        }
        return R.drawable.myicon; // Default
    }
}