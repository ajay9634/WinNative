package com.winlator.cmod.steam.utils

import android.content.Context
import com.winlator.cmod.SetupWizardActivity
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import timber.log.Timber

object ContainerUtils {
    fun getContainerId(appId: String): String {
        return appId
    }

    /**
     * Extracts the game ID from a container ID string
     */
    fun extractGameIdFromContainerId(containerId: String): Int {
        // Remove duplicate suffix like (1), (2) if present
        val idWithoutSuffix = if (containerId.contains("(")) {
            containerId.substringBefore("(")
        } else {
            containerId
        }

        // Split by underscores and find the last numeric part
        val parts = idWithoutSuffix.split("_")
        // The last part should be the numeric ID
        val lastPart = parts.lastOrNull() ?: throw IllegalArgumentException("Invalid container ID format: $containerId")

        return try {
            lastPart.toInt()
        } catch (e: NumberFormatException) {
            Timber.d("extractGameIdFromContainerId: Non-numeric ID '$lastPart' -> hashCode=${lastPart.hashCode()}")
            lastPart.hashCode()
        }
    }

    fun hasContainer(context: Context, containerId: String): Boolean {
        return getContainer(context, containerId) != null
    }

    fun getContainer(context: Context, containerId: String): Container? {
        val containerManager = ContainerManager(context)
        return containerManager.getContainerById(extractGameIdFromContainerId(containerId))
            ?.takeIf { SetupWizardActivity.isContainerUsable(context, it) }
    }

    fun getUsableContainerOrNull(context: Context, appId: String): Container? {
        val containerManager = ContainerManager(context)
        SetupWizardActivity.getPreferredGameContainer(context, containerManager)?.let { return it }

        val containerName = getContainerId(appId)
        return containerManager.containers.firstOrNull {
            it.name == containerName && SetupWizardActivity.isContainerUsable(context, it)
        }
    }

    fun getOrCreateContainer(context: Context, appId: String): Container {
        return getUsableContainerOrNull(context, appId)
            ?: throw IllegalStateException("No installed Wine/Proton container available")
    }

    fun getADrivePath(drives: String): String? {
        for (drive in Container.drivesIterator(drives)) {
            if (drive[0] == "A") {
                return drive[1]
            }
        }
        return null
    }

    fun deleteContainer(context: Context, containerId: String) {
        val containerManager = ContainerManager(context)
        val container = containerManager.getContainerById(extractGameIdFromContainerId(containerId))
        if (container != null) {
            containerManager.removeContainerAsync(container) {}
        }
    }
}
