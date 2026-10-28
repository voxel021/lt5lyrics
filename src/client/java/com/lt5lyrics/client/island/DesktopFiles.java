package com.lt5lyrics.client.island;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

/** SDL's asynchronous picker. Minecraft supplies SDL; AWT is headless. */
public final class DesktopFiles {
    private static final AtomicBoolean OPEN = new AtomicBoolean();
    private static final AtomicReference<Request> ACTIVE = new AtomicReference<>();
    private static final Map<String, Filter> FILTERS = new HashMap<>();
    // A single native callback is deliberately kept alive for the process lifetime.
    // Freeing it from inside the callback, or before it returns on another thread, is unsafe.
    private static SDL_DialogFileCallback callback;

    private record Request(Minecraft mc, boolean save, String extension, BiConsumer<Path, String> done) {}

    // Filters and their UTF-8 strings must stay valid at least until SDL invokes the callback.
    // They are immutable and reused for the two extensions used by the mod (lrc and json).
    private static final class Filter {
        final ByteBuffer name;
        final ByteBuffer pattern;
        final SDL_DialogFileFilter.Buffer entries;
        Filter(String extension) {
            name = MemoryUtil.memUTF8(extension.toUpperCase(Locale.ROOT) + " files");
            pattern = MemoryUtil.memUTF8(extension);
            entries = SDL_DialogFileFilter.calloc(1);
            entries.get(0).name(name).pattern(pattern);
        }
    }

    private DesktopFiles() {}

    public static void choose(boolean save, String title, String extension, String suggested, BiConsumer<Path, String> done) {
        Minecraft mc = Minecraft.getInstance();
        if (!OPEN.compareAndSet(false, true)) {
            mc.execute(() -> done.accept(null, "A file picker is already open."));
            return;
        }
        // SDL requires opening on the main thread; completion may arrive on any thread.
        mc.execute(() -> {
            Request request = new Request(mc, save, extension, done);
            ACTIVE.set(request);
            int properties = 0;
            try {
                if (callback == null) callback = SDL_DialogFileCallback.create(DesktopFiles::completed);
                Filter filter = FILTERS.computeIfAbsent(extension, Filter::new);
                properties = SDLProperties.SDL_CreateProperties();
                if (properties == 0
                        || !SDLProperties.SDL_SetPointerProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_FILTERS_POINTER, filter.entries.address())
                        || !SDLProperties.SDL_SetNumberProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, 1)
                        || !SDLProperties.SDL_SetPointerProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER, mc.getWindow().handle())
                        || (title != null && !SDLProperties.SDL_SetStringProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, title))
                        || (suggested != null && !SDLProperties.SDL_SetStringProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_LOCATION_STRING, suggested))) {
                    throw new IllegalStateException("Could not set file dialog properties");
                }
                int type = save ? SDLDialog.SDL_FILEDIALOG_SAVEFILE : SDLDialog.SDL_FILEDIALOG_OPENFILE;
                SDLDialog.SDL_ShowFileDialogWithProperties(type, callback, 0, properties);
            } catch (Throwable e) {
                // SDL can invoke the callback synchronously. Do not complete twice if it did.
                if (ACTIVE.compareAndSet(request, null)) {
                    OPEN.set(false);
                    done.accept(null, "File picker unavailable: " + e.getClass().getSimpleName());
                }
            } finally {
                // SDL copies the properties on launch; filter data above stays alive.
                if (properties != 0) SDLProperties.SDL_DestroyProperties(properties);
            }
        });
    }

    private static void completed(long userdata, long fileList, int selectedFilter) {
        Request request = ACTIVE.getAndSet(null);
        if (request == null) return;
        Path selected = null;
        String error = null;
        try {
            if (fileList == 0) error = "File picker unavailable.";
            else {
                long first = MemoryUtil.memGetAddress(fileList);
                if (first != 0) {
                    String chosen = MemoryUtil.memUTF8(first);
                    if (chosen != null && !chosen.isBlank()) {
                        selected = Path.of(chosen);
                        if (request.save() && !selected.getFileName().toString().toLowerCase(Locale.ROOT)
                                .endsWith("." + request.extension().toLowerCase(Locale.ROOT))) {
                            selected = selected.resolveSibling(selected.getFileName() + "." + request.extension());
                        }
                    }
                }
            }
        } catch (Throwable e) {
            error = "File picker unavailable: " + e.getClass().getSimpleName();
        }
        Path result = selected;
        String problem = error;
        OPEN.set(false);
        request.mc().execute(() -> {
            if (request.save() && result != null && Files.exists(result)) {
                Screen previous = request.mc().gui.screen();
                request.mc().gui.setScreen(new ConfirmScreen(yes -> {
                    request.mc().gui.setScreen(previous);
                    request.done().accept(yes ? result : null, problem);
                }, Component.translatable("lt5lyrics.file.overwrite.title"),
                   Component.translatable("lt5lyrics.file.overwrite.message")));
            } else {
                request.done().accept(result, problem);
            }
        });
    }
}
