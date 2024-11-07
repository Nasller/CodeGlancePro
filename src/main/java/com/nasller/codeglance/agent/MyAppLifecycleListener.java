package com.nasller.codeglance.agent;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.extensions.PluginId;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

public class MyAppLifecycleListener implements AppLifecycleListener {
    private static final Logger log = LoggerFactory.getLogger(MyAppLifecycleListener.class);

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        injectAgent();
    }

    private static void injectAgent() {
        try {
            IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.findId("com.nasller.CodeGlancePro"));
            if(plugin == null){
                return;
            }
            File[] files = plugin.getPluginPath().resolve("lib").toFile().listFiles(pathname -> {
                String name = pathname.getName();
                return !name.contains("searchableOptions") && name.contains("CodeGlancePro") && name.endsWith(".jar");
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

    private static void injectByteBuddy(){
        try {
            ByteBuddyAgent.install();
            ClassInjector.UsingReflection.ofSystemClassLoader().inject(Map.of(new TypeDescription.ForLoadedType(Interceptor.class),
                    ClassFileLocator.ForClassLoader.read(Interceptor.class)));
            try (Unloaded<EditorImpl> made = new ByteBuddy(ClassFileVersion.ofThisVm(ClassFileVersion.JAVA_V21))
                    .redefine(EditorImpl.class)
                    .method(ElementMatchers.named("getStickyLinesPanelWidth").and(ElementMatchers.returns(int.class)))
                    .intercept(MethodDelegation.to(Interceptor.class))
                    .make()){
                made.load(EditorImpl.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            }
        }catch (Throwable e){
            log.warn("Start CodeGlance Pro Agent error!", e);
        }
    }
}