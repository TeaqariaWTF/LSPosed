/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2020 EdXposed Contributors
 * Copyright (C) 2021 LSPosed Contributors
 */

package io.github.lsposed.manager.adapters;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.lsposed.manager.App;
import io.github.lsposed.manager.BuildConfig;
import io.github.lsposed.manager.ConfigManager;
import io.github.lsposed.manager.R;
import io.github.lsposed.manager.ui.activity.AppListActivity;
import io.github.lsposed.manager.ui.fragment.CompileDialogFragment;
import io.github.lsposed.manager.util.GlideApp;
import io.github.lsposed.manager.util.ModuleUtil;
import rikka.core.res.ResourcesKt;
import rikka.widget.switchbar.SwitchBar;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;

@SuppressLint("NotifyDataSetChanged")
public class ScopeAdapter extends RecyclerView.Adapter<ScopeAdapter.ViewHolder> implements Filterable {

    private final AppListActivity activity;
    private final PackageManager pm;
    private final SharedPreferences preferences;
    private final String modulePackageName;
    private final String moduleName;
    private final SwitchBar masterSwitch;
    private final List<Integer> moduleList = new ArrayList<>();
    private final List<Integer> recommendedList = new ArrayList<>();
    private final List<AppInfo> searchList = new ArrayList<>();
    private List<AppInfo> showList = new ArrayList<>();
    private List<Integer> checkedList = new ArrayList<>();
    private boolean enabled = true;
    private ApplicationInfo selectedInfo;

    public ScopeAdapter(AppListActivity activity, String moduleName, String modulePackageName, SwitchBar masterSwitch) {
        this.activity = activity;
        this.moduleName = moduleName;
        this.modulePackageName = modulePackageName;
        this.masterSwitch = masterSwitch;
        preferences = App.getPreferences();
        pm = activity.getPackageManager();
        masterSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            if (!ModuleUtil.getInstance().setModuleEnabled(modulePackageName, isChecked)) {
                return false;
            }
            enabled = isChecked;
            notifyDataSetChanged();
            return true;
        });
        refresh();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(activity).inflate(R.layout.item_module, parent, false);
        return new ViewHolder(v);
    }

    private void loadApps() {
        List<PackageInfo> appList = ConfigManager.getInstalledPackagesFromAllUsers(PackageManager.GET_META_DATA);
        checkedList = ConfigManager.getModuleScope(modulePackageName);
        moduleList.clear();
        recommendedList.clear();
        searchList.clear();
        showList.clear();

        enabled = ModuleUtil.getInstance().isModuleEnabled(modulePackageName);
        activity.runOnUiThread(() -> masterSwitch.setChecked(enabled));

        ArrayList<Integer> installedList = new ArrayList<>();
        List<String> recommendedPackageNames = ModuleUtil.getInstance().getModule(modulePackageName).getScopeList();
        Map<String, ArrayList<PackageInfo>> sharedUidPackages = new HashMap<>();
        for (PackageInfo info : appList) {
            int uid = info.applicationInfo.uid;
            if (!installedList.contains(uid)) installedList.add(uid);
            if (info.packageName.equals(this.modulePackageName)) {
                if (!checkedList.contains(uid)) checkedList.add(uid);
                if (!moduleList.contains(uid)) moduleList.add(uid);
            }
            if (recommendedPackageNames != null && recommendedPackageNames.contains(info.packageName) && !recommendedList.contains(uid)) {
                recommendedList.add(uid);
            }

            if (shouldHideApp(info)) {
                continue;
            }

            if (info.sharedUserId != null) {
                ArrayList<PackageInfo> packageInfos = sharedUidPackages.computeIfAbsent(info.sharedUserId + "!" + uid / 100000, k -> new ArrayList<>());
                packageInfos.add(info);
            } else {
                AppInfo appInfo = new AppInfo();
                appInfo.packageInfo = info;
                appInfo.label = getAppLabel(info.applicationInfo, pm);
                searchList.add(appInfo);
            }

        }
        for (List<PackageInfo> packageInfos : sharedUidPackages.values()) {
            AppInfo appInfo = new AppInfo();
            String[] packageLabels = new String[packageInfos.size()];
            String name = null;

            for (int i = 0; i < packageLabels.length; i++) {
                ApplicationInfo ai = packageInfos.get(i).applicationInfo;
                CharSequence label = ai.loadLabel(pm);
                if (label != null) {
                    packageLabels[i] = label.toString();
                }
                if (ai.icon != 0) {
                    appInfo.packageInfo = packageInfos.get(i);
                    break;
                }
            }

            if (packageLabels.length == 1) {
                name = packageLabels[0];
            } else {
                for (PackageInfo packageInfo : packageInfos) {
                    if (packageInfo.sharedUserLabel != 0) {
                        final CharSequence nm = pm.getText(packageInfo.packageName, packageInfo.sharedUserLabel, packageInfo.applicationInfo);
                        if (nm != null) {
                            name = nm.toString();
                            appInfo.packageInfo = packageInfo;
                            break;
                        }
                    }
                }
            }

            if (name == null) {
                name = packageInfos.get(0).sharedUserId;
            }

            appInfo.label = String.format("[SharedUID] %s", name);

            if (appInfo.packageInfo != null) {
                searchList.add(appInfo);
            }
        }
        checkedList.retainAll(installedList);
        if (selectedNothing() && hasRecommended()) {
            checkRecommended();
        }
        showList = sortApps(searchList);
        activity.onDataReady();
    }

    private boolean shouldHideApp(PackageInfo info) {
        if (info.packageName.equals(this.modulePackageName)) {
            return true;
        }

        if (info.packageName.equals(BuildConfig.APPLICATION_ID)) {
            return true;
        }
        if (checkedList.contains(info.applicationInfo.uid) || info.packageName.equals("android")) {
            return false;
        }
        if (!preferences.getBoolean("show_modules", false)) {
            if (info.applicationInfo.metaData != null && info.applicationInfo.metaData.containsKey("xposedmodule")) {
                return true;
            }
        }
        if (!preferences.getBoolean("show_games", false)) {
            if (info.applicationInfo.category == ApplicationInfo.CATEGORY_GAME) {
                return true;
            }
            //noinspection deprecation
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_IS_GAME) != 0) {
                return true;
            }
        }
        if ((info.applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) == 0) {
            return true;
        }
        return !preferences.getBoolean("show_system_apps", false) && (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private List<AppInfo> sortApps(List<AppInfo> list) {
        Comparator<PackageInfo> comparator = AppHelper.getAppListComparator(preferences.getInt("list_sort", 0), pm);
        Comparator<PackageInfo> frameworkComparator = (a, b) -> {
            if (a.packageName.equals("android") == b.packageName.equals("android")) {
                return comparator.compare(a, b);
            } else if (a.packageName.equals("android")) {
                return -1;
            } else {
                return 1;
            }
        };
        Comparator<PackageInfo> recommendedComparator = (a, b) -> {
            boolean aRecommended = hasRecommended() && recommendedList.contains(a.applicationInfo.uid);
            boolean bRecommended = hasRecommended() && recommendedList.contains(b.applicationInfo.uid);
            if (aRecommended == bRecommended) {
                return frameworkComparator.compare(a, b);
            } else if (aRecommended) {
                return -1;
            } else {
                return 1;
            }
        };
        list.sort((a, b) -> {
            boolean aChecked = checkedList.contains(a.packageInfo.applicationInfo.uid);
            boolean bChecked = checkedList.contains(b.packageInfo.applicationInfo.uid);
            if (aChecked == bChecked) {
                return recommendedComparator.compare(a.packageInfo, b.packageInfo);
            } else if (aChecked) {
                return -1;
            } else {
                return 1;
            }
        });
        return list;
    }

    private void checkRecommended() {
        checkedList.addAll(recommendedList);
        ConfigManager.setModuleScope(modulePackageName, checkedList);
    }

    private boolean hasRecommended() {
        return !recommendedList.isEmpty();
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.use_recommended) {
            if (!checkedList.isEmpty()) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.use_recommended)
                        .setMessage(R.string.use_recommended_message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            checkRecommended();
                            notifyDataSetChanged();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else {
                checkRecommended();
                notifyDataSetChanged();
            }
            return true;
        } else if (itemId == R.id.item_show_system) {
            item.setChecked(!item.isChecked());
            preferences.edit().putBoolean("show_system_apps", item.isChecked()).apply();
        } else if (itemId == R.id.item_show_games) {
            item.setChecked(!item.isChecked());
            preferences.edit().putBoolean("show_games", item.isChecked()).apply();
        } else if (itemId == R.id.item_show_modules) {
            item.setChecked(!item.isChecked());
            preferences.edit().putBoolean("show_modules", item.isChecked()).apply();
        } else if (itemId == R.id.menu_launch) {
            Intent launchIntent = AppHelper.getSettingsIntent(modulePackageName, pm);
            if (launchIntent != null) {
                activity.startActivity(launchIntent);
            } else {
                activity.makeSnackBar(R.string.module_no_ui, Snackbar.LENGTH_LONG);
            }
            return true;
        } else if (itemId == R.id.backup) {
            Calendar now = Calendar.getInstance();
            activity.backupLauncher.launch(String.format(Locale.US,
                    "%s_%04d%02d%02d_%02d%02d%02d.lsp",
                    moduleName,
                    now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
                    now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE), now.get(Calendar.SECOND)));
            return true;
        } else if (itemId == R.id.restore) {
            activity.restoreLauncher.launch(new String[]{"*/*"});
            return true;
        } else if (!AppHelper.onOptionsItemSelected(item, preferences)) {
            return false;
        }
        refresh();
        return true;
    }

    public boolean onContextItemSelected(@NonNull MenuItem item) {
        ApplicationInfo info = selectedInfo;
        if (info == null) {
            return false;
        }
        int itemId = item.getItemId();
        if (itemId == R.id.menu_launch) {
            Intent launchIntent = pm.getLaunchIntentForPackage(info.packageName);
            if (launchIntent != null) {
                activity.startActivity(launchIntent);
            }
        } else if (itemId == R.id.menu_compile_speed) {
            CompileDialogFragment.speed(activity.getSupportFragmentManager(), info);
        } else if (itemId == R.id.menu_app_store) {
            Uri uri = Uri.parse("market://details?id=" + info.packageName);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                activity.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (itemId == R.id.menu_app_info) {
            activity.startActivity(new Intent(ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", info.packageName, null)));
        } else {
            return false;
        }
        return true;
    }

    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_app_list, menu);
        Intent intent = AppHelper.getSettingsIntent(modulePackageName, pm);
        if (intent == null) {
            menu.removeItem(R.id.menu_launch);
        }
        if (!hasRecommended()) {
            menu.removeItem(R.id.use_recommended);
        }
        menu.findItem(R.id.item_show_system).setChecked(preferences.getBoolean("show_system_apps", false));
        menu.findItem(R.id.item_show_games).setChecked(preferences.getBoolean("show_games", false));
        menu.findItem(R.id.item_show_modules).setChecked(preferences.getBoolean("show_modules", false));
        switch (preferences.getInt("list_sort", 0)) {
            case 7:
                menu.findItem(R.id.item_sort_by_update_time_reverse).setChecked(true);
                break;
            case 6:
                menu.findItem(R.id.item_sort_by_update_time).setChecked(true);
                break;
            case 5:
                menu.findItem(R.id.item_sort_by_install_time_reverse).setChecked(true);
                break;
            case 4:
                menu.findItem(R.id.item_sort_by_install_time).setChecked(true);
                break;
            case 3:
                menu.findItem(R.id.item_sort_by_package_name_reverse).setChecked(true);
                break;
            case 2:
                menu.findItem(R.id.item_sort_by_package_name).setChecked(true);
                break;
            case 1:
                menu.findItem(R.id.item_sort_by_name_reverse).setChecked(true);
                break;
            case 0:
                menu.findItem(R.id.item_sort_by_name).setChecked(true);
                break;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.root.setAlpha(enabled ? 1.0f : .5f);
        AppInfo appInfo = showList.get(position);
        boolean android = appInfo.packageInfo.packageName.equals("android");
        CharSequence appName;
        int userId = appInfo.packageInfo.applicationInfo.uid / 100000;
        if (userId != 0) {
            appName = String.format("%s (%s)", android ? activity.getString(R.string.android_framework) : appInfo.label, userId);
        } else {
            appName = android ? activity.getString(R.string.android_framework) : appInfo.label;
        }
        holder.appName.setText(appName);
        GlideApp.with(holder.appIcon)
                .load(appInfo.packageInfo)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        holder.appIcon.setImageDrawable(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {

                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        holder.appIcon.setImageDrawable(pm.getDefaultActivityIcon());
                    }
                });
        SpannableStringBuilder sb = new SpannableStringBuilder(android ? "" : activity.getString(R.string.app_description, appInfo.packageInfo.packageName, appInfo.packageInfo.versionName));
        holder.appDescription.setVisibility(View.VISIBLE);
        if (hasRecommended() && recommendedList.contains(appInfo.packageInfo.applicationInfo.uid)) {
            if (!android) sb.append("\n");
            String recommended = activity.getString(R.string.requested_by_module);
            sb.append(recommended);
            final ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(ResourcesKt.resolveColor(activity.getTheme(), R.attr.colorAccent));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                final TypefaceSpan typefaceSpan = new TypefaceSpan(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                sb.setSpan(typefaceSpan, sb.length() - recommended.length(), sb.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            } else {
                final StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);
                sb.setSpan(styleSpan, sb.length() - recommended.length(), sb.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            }
            sb.setSpan(foregroundColorSpan, sb.length() - recommended.length(), sb.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        } else if (android) {
            holder.appDescription.setVisibility(View.GONE);
        }
        holder.appDescription.setText(sb);

        holder.itemView.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
            activity.getMenuInflater().inflate(R.menu.menu_app_item, menu);
            Intent launchIntent = pm.getLaunchIntentForPackage(appInfo.packageInfo.packageName);
            if (launchIntent == null) {
                menu.removeItem(R.id.menu_launch);
            }
            if (android) {
                menu.removeItem(R.id.menu_compile_speed);
                menu.removeItem(R.id.menu_app_store);
            }
        });

        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(checkedList.contains(appInfo.packageInfo.applicationInfo.uid));

        holder.checkbox.setOnCheckedChangeListener((v, isChecked) -> onCheckedChange(v, isChecked, appInfo.packageInfo.applicationInfo.uid));
        holder.itemView.setOnClickListener(v -> {
            if (enabled) holder.checkbox.toggle();
        });
        holder.itemView.setOnLongClickListener(v -> {
            selectedInfo = appInfo.packageInfo.applicationInfo;
            return false;
        });
    }

    @Override
    public long getItemId(int position) {
        PackageInfo info = showList.get(position).packageInfo;
        return (info.packageName  + "!" + info.applicationInfo.uid / 100000).hashCode();
    }

    @Override
    public Filter getFilter() {
        return new ApplicationFilter();
    }

    @Override
    public int getItemCount() {
        return showList.size();
    }

    public void refresh() {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(this::loadApps);
    }

    protected void onCheckedChange(CompoundButton buttonView, boolean isChecked, int uid) {
        if (isChecked) {
            checkedList.add(uid);
        } else {
            checkedList.remove((Integer) uid);
        }
        if (!ConfigManager.setModuleScope(modulePackageName, checkedList)) {
            activity.makeSnackBar(R.string.failed_to_save_scope_list, Snackbar.LENGTH_SHORT);
            if (!isChecked) {
                checkedList.add(uid);
            } else {
                checkedList.remove((Integer) uid);
            }
            buttonView.setChecked(!isChecked);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        View root;
        ImageView appIcon;
        TextView appName;
        TextView appDescription;
        MaterialCheckBox checkbox;

        ViewHolder(View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.item_root);
            appIcon = itemView.findViewById(R.id.app_icon);
            appName = itemView.findViewById(R.id.app_name);
            appDescription = itemView.findViewById(R.id.description);
            checkbox = itemView.findViewById(R.id.checkbox);
            checkbox.setVisibility(View.VISIBLE);
        }
    }

    private class ApplicationFilter extends Filter {

        private boolean lowercaseContains(String s, String filter) {
            return !TextUtils.isEmpty(s) && s.toLowerCase().contains(filter);
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (constraint.toString().isEmpty()) {
                showList = searchList;
            } else {
                ArrayList<AppInfo> filtered = new ArrayList<>();
                String filter = constraint.toString().toLowerCase();
                for (AppInfo info : searchList) {
                    if (lowercaseContains(info.label.toString(), filter)
                            || lowercaseContains(info.packageInfo.packageName, filter)) {
                        filtered.add(info);
                    }
                }
                showList = filtered;
            }
            return null;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            notifyDataSetChanged();
        }
    }

    private boolean selectedNothing() {
        List<Integer> list = new ArrayList<>(checkedList);
        list.removeAll(moduleList);
        return list.isEmpty();
    }

    public boolean onBackPressed() {
        if (masterSwitch.isChecked() && selectedNothing()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.use_recommended);
            builder.setMessage(hasRecommended() ? R.string.no_scope_selected_has_recommended : R.string.no_scope_selected);
            if (hasRecommended()) {
                builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    checkRecommended();
                    notifyDataSetChanged();
                });
            } else {
                builder.setPositiveButton(android.R.string.cancel, null);
            }
            builder.setNegativeButton(hasRecommended() ? android.R.string.cancel : android.R.string.ok, (dialog, which) -> {
                ModuleUtil.getInstance().setModuleEnabled(modulePackageName, false);
                Toast.makeText(activity, activity.getString(R.string.module_disabled_no_selection, moduleName), Toast.LENGTH_LONG).show();
                activity.finish();
            });
            builder.show();
            return false;
        } else {
            return true;
        }
    }

    public static String getAppLabel(ApplicationInfo info, PackageManager pm) {
        return info.loadLabel(pm).toString();
    }

    public static class AppInfo {
        public PackageInfo packageInfo;
        public CharSequence label = null;
    }
}
