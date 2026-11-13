package steamstone.inject;

import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;

import steamstone.exception.StoneException;
import steamstone.ui.library.LibraryView;

/**
 * Composition root shared by the desktop launcher and the optional UI verification tool.
 */
public final class SteamStoneInjector {

    public static Injector create() {
        try {
            return Guice.createInjector(new SteamStoneModule());
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
