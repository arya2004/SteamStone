package steamstone.inject;

import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.ProvisionException;

import steamstone.exception.StoneException;
import steamstone.ui.library.LibraryView;

/**
 * Composition root shared by the desktop launcher and the optional UI verification tool.
 */
public final class SteamStoneInjector {
    private static final String CLASS_LOADING_PROPERTY = "guice_custom_class_loading";
    private static final String CHILD_CLASS_LOADING = "CHILD";

    public static Injector create() {
        return create(new SteamStoneModule());
    }

    public static Injector create(final Module... modules) {
        // Use Guice's child loader instead of its Unsafe-based hidden-class definer on JDK 25.
        System.setProperty(CLASS_LOADING_PROPERTY, CHILD_CLASS_LOADING);
        try {
            return Guice.createInjector(modules);
        } catch (final CreationException e) {
            throw new StoneException("Could not configure SteamStone's dependencies.", e);
        }
    }

    public static LibraryView createView() {
        try {
            return create().getInstance(LibraryView.class);
        } catch (final ProvisionException e) {
            throw new StoneException("Could not initialize SteamStone's interface.", e);
        }
    }
}
