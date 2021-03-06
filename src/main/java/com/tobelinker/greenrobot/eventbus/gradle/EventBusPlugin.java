package com.tobelinker.greenrobot.eventbus.gradle;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.internal.dsl.ProductFlavor;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class EventBusPlugin implements Plugin<Project>{
    @Override
    public void apply(Project project) {
        if(!project.getPlugins().hasPlugin("android") || ! project.getPlugins().hasPlugin("com.android.application")){
            throw new IllegalStateException("EventBus plugin can only be applied to android projects!");
        }

        project.getExtensions().create("greenrobot-eventbus", EventBusExtension.class);


        AppExtension android = (AppExtension) project.getExtensions().findByName("android");

        if(android != null){
            project.afterEvaluate(new Action<Project>() {
                @Override
                public void execute(Project project) {
                    findAndHookProguardTask(project, android);
                }
            });
        }
    }

    private void findAndHookProguardTask(Project project, AppExtension android) {
        DomainObjectSet<ApplicationVariant> applicationVariants = android.getApplicationVariants();


        for(ApplicationVariant variant : applicationVariants){
            String eventBusIndex = null;

            ProductFlavor defaultConfig = android.getDefaultConfig();
            eventBusIndex = readEventBusIndexFromAnnotationProcessorOptions(defaultConfig);
            if(eventBusIndex == null || eventBusIndex.isEmpty()){
                eventBusIndex = readEventBusIndexFromAndroidApt(project);

                if(eventBusIndex == null || eventBusIndex.isEmpty()){
                    eventBusIndex = readEventBusIndexFromExtension(project);
                }
            }

            if(eventBusIndex == null || eventBusIndex.isEmpty()){
                throw new IllegalStateException("eventBusIndex is null or empty!");
            }
            String name = variant.getName();
            Task proguardTask = project.getTasks().findByName("transformClassesAndResourcesWithProguardFor"+capitalize(name));
            if(proguardTask != null){
                hookProguardTask(proguardTask, eventBusIndex);
            }
        }
    }


    private String readEventBusIndexFromAnnotationProcessorOptions(ProductFlavor defaultConfig) {
        try {
            String eventBusIndex = null;
            Method getJavaCompileOptions = defaultConfig.getClass().getDeclaredMethod("getJavaCompileOptions");
            Object javaCompileOptions = getJavaCompileOptions.invoke(defaultConfig);
            Method getAnnotationProcessorOptions = javaCompileOptions.getClass().getDeclaredMethod("getAnnotationProcessorOptions");
            Object annotationProcessorOptions = getAnnotationProcessorOptions.invoke(javaCompileOptions);
            Method getArguments = annotationProcessorOptions.getClass().getDeclaredMethod("getArguments");
            Map<String, String> arguments = (Map<String, String>) getArguments.invoke(annotationProcessorOptions);
            eventBusIndex = arguments.get("eventBusIndex");
            return eventBusIndex;
        } catch (Exception e) {
        }

        return null;
    }

    private String readEventBusIndexFromAndroidApt(Project project){
        try {
            String eventBusIndex = null;
            Object androidAptExtension = project.getExtensions().findByName("apt");

            if(androidAptExtension != null){
                Method arguments = androidAptExtension.getClass().getSuperclass().getDeclaredMethod("arguments");
                Object aptarguments = arguments.invoke(androidAptExtension);
                List options = (List) aptarguments;
                for(Object argument : options){
                    String[] temp = (argument.toString()).split("=");
                    if(temp.length == 2){
                        if(temp[0].equals("-AeventBusIndex")){
                        System.out.println(temp[1]);
                            return temp[1];
                        }
                    }
                }
                return eventBusIndex;
            }
        }catch (Exception e){

        }
        return null;
    }

    @NotNull
    private String readEventBusIndexFromExtension(Project project) {
        String eventBusIndex = null;
        EventBusExtension eventBusExtension = (EventBusExtension) project.getExtensions().findByName("greenrobot-eventbus");
        if (eventBusExtension != null) {
            eventBusIndex = eventBusExtension.getEventBusIndex();
        }
        if (eventBusIndex == null || eventBusIndex.isEmpty()) {
            throw new IllegalStateException("eventBusIndex is null or emply!");
        }
        return eventBusIndex;
    }

    private void hookProguardTask(final Task proguardTask, final String eventBusIndex) {

        proguardTask.doLast(new Action<Task>() {
            @Override
            public void execute(Task task) {
                File proguardedJarFile = null;
                File mappingFile = null;
                FileCollection files = proguardTask.getOutputs().getFiles();

                for(File file : files){
                    if(proguardedJarFile != null && mappingFile != null){
                        break;
                    }
                    if(file.isDirectory()){
                        if(proguardedJarFile == null){
                            proguardedJarFile = findFile(file, "main.jar");
                        }
                    }else{
                        if (file.getName().equals("mapping.txt")) {
                            mappingFile = file;
                        }
                    }
                }


                if (proguardedJarFile != null && mappingFile != null) {
                    try {
                        SubscriberInfoIndexProcessor.process(proguardedJarFile, mappingFile, eventBusIndex, "<clinit>", null);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

            }
        });
    }

    public static String capitalize(CharSequence self) {
        return self.length() == 0?"":"" + Character.toUpperCase(self.charAt(0)) + self.subSequence(1, self.length());
    }

    public static File findFile(File dir, String file) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                String fileName = files[i].getName();
                if (files[i].isDirectory()) {
                    return findFile(files[i], file);
                } else if (fileName.endsWith(file)) {
                    return files[i];
                } else {
                    continue;
                }
            }

        }
        return null;
    }
}
