package app.revanced.utils.patcher

import app.revanced.cli.command.MainCommand
import app.revanced.cli.command.MainCommand.args
import app.revanced.cli.command.MainCommand.logger
import app.revanced.patcher.Patcher
import app.revanced.patcher.data.Data
import app.revanced.patcher.extensions.PatchExtensions.compatiblePackages
import app.revanced.patcher.extensions.PatchExtensions.deprecated
import app.revanced.patcher.extensions.PatchExtensions.include
import app.revanced.patcher.extensions.PatchExtensions.patchName
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.util.patch.impl.JarPatchBundle

fun Patcher.addPatchesFiltered() {
    val packageName = this.data.packageMetadata.packageName
    val packageVersion = this.data.packageMetadata.packageVersion

    args.patchArgs?.patchBundles!!.forEach { bundle ->
        val includedPatches = mutableListOf<Class<out Patch<Data>>>()
        JarPatchBundle(bundle).loadPatches().forEach patch@{ patch ->
            val compatiblePackages = patch.compatiblePackages
            val patchName = patch.patchName

            val prefix = "Skipping $patchName, reason"

            val args = MainCommand.args.patchArgs?.patchingArgs!!

            if (args.excludedPatches.contains(patchName)) {
                logger.info("$prefix: manually excluded")
                return@patch
            } else if ((!patch.include || args.defaultExclude) && !args.includedPatches.contains(patchName)) {
                logger.info("$prefix: excluded by default")
                return@patch
            }

            patch.deprecated?.let { (reason, replacement) ->
                logger.warn("$prefix: deprecated: '$reason'" + if (replacement != null) ". Use '$replacement' instead." else "")
                return@patch
            }

            if (compatiblePackages == null) logger.warn("$prefix: Missing compatibility annotation. Continuing.")
            else {
                if (!compatiblePackages.any { it.name == packageName }) {
                    logger.warn("$prefix: incompatible with $packageName. This patch is only compatible with ${
                        compatiblePackages.joinToString(
                            ", "
                        ) { it.name }
                    }")
                    return@patch
                }

                if (!(args.experimental || compatiblePackages.any { it.versions.isEmpty() || it.versions.any { version -> version == packageVersion } })) {
                    val compatibleWith = compatiblePackages.joinToString(";") { _package ->
                        "${_package.name}: ${_package.versions.joinToString(", ")}"
                    }
                    logger.warn("$prefix: incompatible with version $packageVersion. This patch is only compatible with version $compatibleWith")
                    return@patch
                }
            }

            logger.trace("Adding $patchName")
            includedPatches.add(patch)
        }
        this.addPatches(includedPatches)
    }
}

fun Patcher.applyPatchesVerbose() {
    this.applyPatches().forEach { (patch, result) ->
        if (result.isSuccess) {
            logger.info("$patch succeeded")
            return@forEach
        }
        logger.error("$patch failed:")
        result.exceptionOrNull()!!.printStackTrace()
    }
}

fun Patcher.mergeFiles() {
    this.addFiles(args.patchArgs?.patchingArgs!!.mergeFiles) { file ->
        logger.info("Merging $file")
    }
}
