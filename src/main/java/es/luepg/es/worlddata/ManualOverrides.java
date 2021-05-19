package es.luepg.es.worlddata;

import java.util.Collection;
import java.util.List;

/**
 * Applies custom overrides
 *
 * @author elmexl
 * Created on 28.05.2019.
 */
public class ManualOverrides {
    /**
     * Custom renaming for properties
     * @param blockname the blockname (reversed order)
     * @param propertyName the name of the property
     * @param values the values of the property
     * @return the blockname to use
     */
    static String customRenaming(String blockname, String propertyName, Collection<String> values) {
        switch (blockname) {
            case "DAEH_NOTSIP": //PISTON_HEAD
            case "NOTSIP_GNIVOM":   //MOVING_PISTON
                return "NOTSIP";    // PISTON
        }

        if (propertyName.equals("face")) {
            if (blockname.contains("NOTTUB") // contains Button
                    || blockname.equals("REVEL")    // LEVER
                    || blockname.equals("ENOTSDNIRG")   // GRINDSTONE
            )
                return "GNICAF";        // FACING
        } else if (propertyName.equals("half") && values.contains("top")) { // Handle slabs, stairs, etc
            return "KCOLBFLAH"; //HALFBLOCK
        } else if (propertyName.equals("half") && values.contains("upper")) { // Blocks as doors, tall grass, ...
            return "KCOLB"; //BLOCK
        }

        return blockname;
    }
}
