package com.alram.mail.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 설치된 앱(런처에 보이는 앱)을 규칙 테이블에 등록해서 앱 목록 화면에 나오게 한다. */
class AppCatalog(private val context: Context, private val dao: AppRuleDao) {
    suspend fun sync() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .forEach { info ->
                dao.insertIgnore(
                    AppRuleEntity(
                        packageName = info.packageName,
                        label = info.loadLabel(pm).toString(),
                        isSystem = isSystem(info),
                    ),
                )
            }
    }

    companion object {
        fun isSystem(info: ApplicationInfo): Boolean =
            (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

        fun labelOf(pm: PackageManager, pkg: String): String = runCatching {
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrDefault(pkg)

        fun isSystemPackage(pm: PackageManager, pkg: String): Boolean = runCatching {
            isSystem(pm.getApplicationInfo(pkg, 0))
        }.getOrDefault(false)
    }
}
