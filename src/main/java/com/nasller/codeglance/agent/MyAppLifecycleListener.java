package com.nasller.codeglance.agent;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import kotlinx.coroutines.repackaged.net.bytebuddy.agent.ByteBuddyAgent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.List;

public class MyAppLifecycleListener implements AppLifecycleListener {
    private static final Logger log = LoggerFactory.getLogger(MyAppLifecycleListener.class);

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        try {
            IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.findId("com.nasller.CodeGlancePro"));
            if(plugin == null){
                return;
            }
            File[] files = plugin.getPluginPath().resolve("lib").toFile().listFiles(pathname -> {
                String name = pathname.getName();
                return name.startsWith("instrumented-CodeGlancePro-") && name.endsWith(".jar");
            });
            if(files == null || files.length == 0){
                log.warn("CodeGlance Pro not found!");
                return;
            }
            File file = files[0];
            if(!file.isFile()) {
                log.warn("CodeGlance Pro not found!");
                return;
            }
            String runtimeMxBeanName = ManagementFactory.getRuntimeMXBean().getName();
            String pid = runtimeMxBeanName.substring(0, runtimeMxBeanName.indexOf('@'));
            ByteBuddyAgent.attach(file,pid);
        }catch (Throwable e){
            log.warn("Start CodeGlance Pro Agent error!", e);
        }
    }
}