/*
 *  This file is part of nzyme.
 *
 *  nzyme is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  nzyme is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with nzyme.  If not, see <http://www.gnu.org/licenses/>.
 */

package horse.wtf.nzyme.configuration.leader;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import horse.wtf.nzyme.Role;
import horse.wtf.nzyme.alerts.Alert;
import horse.wtf.nzyme.configuration.*;
import horse.wtf.nzyme.dot11.deception.traps.Trap;
import horse.wtf.nzyme.notifications.uplinks.graylog.GraylogAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LeaderConfigurationLoader {

    private static final Logger LOG = LogManager.getLogger(LeaderConfigurationLoader.class);

    private final Config root;
    private final Config general;
    private final Config interfaces;
    private final Config python;
    private final Config alerting;
    private final Config trackerDevice;

    public LeaderConfigurationLoader(File configFile, boolean skipValidation) throws InvalidConfigurationException, IncompleteConfigurationException, FileNotFoundException {
        if (!Files.isReadable(configFile.toPath())) {
            throw new FileNotFoundException("File at [" + configFile.getPath() + "] does not exist or is not readable. Check path and permissions.");
        }

        this.root = ConfigFactory.parseFile(configFile).resolve();

        try {
            this.general = root.getConfig(ConfigurationKeys.GENERAL);
            this.python = general.getConfig(ConfigurationKeys.PYTHON);
            this.alerting = general.getConfig(ConfigurationKeys.ALERTING);
            this.interfaces = root.getConfig(ConfigurationKeys.INTERFACES);
            this.trackerDevice = root.getConfig(ConfigurationKeys.TRACKER_DEVICE);
        } catch(ConfigException e) {
            throw new IncompleteConfigurationException("Incomplete configuration.", e);
        }

        if (!skipValidation) {
            validate();
        }
    }

    public LeaderConfiguration get() {
        return LeaderConfiguration.create(
                parseVersionchecksEnabled(),
                parseFetchOUIsEnabled(),
                parseRole(),
                parseNzymeId(),
                parseAdminPasswordHash(),
                parseDatabasePath(),
                parsePythonExecutable(),
                parsePythonScriptDirectory(),
                parsePythonScriptPrefix(),
                parseRestListenUri(),
                parseHttpExternalUri(),
                parseUseTls(),
                parseTlsCertificatePath(),
                parseTlsKeyPath(),
                parseDot11Monitors(),
                parseDot11Networks(),
                parseDot11TrapDeviceDefinitions(),
                parseDot11Alerts(),
                parseAlertingRetentionPeriodMinutes(),
                parseAlertingTrainingPeriodSeconds(),
                parseGraylogUplinks(),
                parseTrackerDevice()
        );
    }

    private Role parseRole() {
        return general.getEnum(Role.class, ConfigurationKeys.ROLE);
    }

    private String parseNzymeId() {
        return general.getString(ConfigurationKeys.ID);
    }

    private String parseAdminPasswordHash() {
        return general.getString(ConfigurationKeys.ADMIN_PASSWORD_HASH);
    }

    private String parseDatabasePath() {
        return general.getString(ConfigurationKeys.DATABASE_PATH);
    }

    private String parsePythonExecutable() {
        return python.getString(ConfigurationKeys.PYTHON_PATH);
    }

    private String parsePythonScriptDirectory() {
        return python.getString(ConfigurationKeys.PYTHON_SCRIPT_DIR);
    }

    private String parsePythonScriptPrefix() {
        return python.getString(ConfigurationKeys.PYTHON_SCRIPT_PREFIX);
    }

    private boolean parseVersionchecksEnabled() {
        return general.getBoolean(ConfigurationKeys.VERSIONCHECKS);
    }

    private boolean parseFetchOUIsEnabled() {
        return general.getBoolean(ConfigurationKeys.FETCH_OUIS);
    }

    private boolean parseUseTls() {
        return interfaces.getBoolean(ConfigurationKeys.USE_TLS);
    }

    private Path parseTlsCertificatePath() {
        return new File(interfaces.getString(ConfigurationKeys.TLS_CERTIFICATE_PATH)).toPath();
    }

    private Path parseTlsKeyPath() {
        return new File(interfaces.getString(ConfigurationKeys.TLS_KEY_PATH)).toPath();
    }

    private URI parseRestListenUri() {
        return URI.create(interfaces.getString(ConfigurationKeys.REST_LISTEN_URI));
    }

    private URI parseHttpExternalUri() {
        return URI.create(interfaces.getString(ConfigurationKeys.HTTP_EXTERNAL_URI));
    }

    private Integer parseAlertingRetentionPeriodMinutes() {
        return alerting.getInt(ConfigurationKeys.CLEAN_AFTER_MINUTES);
    }

    private Integer parseAlertingTrainingPeriodSeconds() {
        return alerting.getInt(ConfigurationKeys.TRAINING_PERIOD_SECONDS);
    }

    private List<Dot11MonitorDefinition> parseDot11Monitors() {
        ImmutableList.Builder<Dot11MonitorDefinition> result = new ImmutableList.Builder<>();

        for (Config config : root.getConfigList(ConfigurationKeys.DOT11_MONITORS)) {
            if (!Dot11MonitorDefinition.checkConfig(config)) {
                LOG.info("Skipping 802.11 monitor with invalid configuration. Invalid monitor: [{}]", config);
                continue;
            }

            result.add(Dot11MonitorDefinition.create(
                    config.getString(ConfigurationKeys.DEVICE),
                    config.getIntList(ConfigurationKeys.CHANNELS),
                    config.getString(ConfigurationKeys.HOP_COMMAND),
                    config.getInt(ConfigurationKeys.HOP_INTERVAL)
            ));
        }

        return result.build();
    }

    private List<Dot11NetworkDefinition> parseDot11Networks() {
        ImmutableList.Builder<Dot11NetworkDefinition> result = new ImmutableList.Builder<>();

        for (Config config : root.getConfigList(ConfigurationKeys.DOT11_NETWORKS)) {
            if (!Dot11NetworkDefinition.checkConfig(config)) {
                LOG.info("Skipping 802.11 network with invalid configuration. Invalid network: [{}]", config);
                continue;
            }

            ImmutableList.Builder<Dot11BSSIDDefinition> lowercaseBSSIDs = new ImmutableList.Builder<>();
            for (Config bssid : config.getConfigList(ConfigurationKeys.BSSIDS)) {
                lowercaseBSSIDs.add(Dot11BSSIDDefinition.create(
                        bssid.getString(ConfigurationKeys.ADDRESS).toLowerCase(),
                        bssid.getStringList(ConfigurationKeys.FINGERPRINTS)
                ));
            }

            result.add(Dot11NetworkDefinition.create(
                    config.getString(ConfigurationKeys.SSID),
                    lowercaseBSSIDs.build(),
                    config.getIntList(ConfigurationKeys.CHANNELS),
                    config.getStringList(ConfigurationKeys.SECURITY),
                    config.getInt(ConfigurationKeys.BEACON_RATE)
            ));
        }

        return result.build();
    }

    private List<Dot11TrapDeviceDefinition> parseDot11TrapDeviceDefinitions() {
        ImmutableList.Builder<Dot11TrapDeviceDefinition> result = new ImmutableList.Builder<>();

        for (Config config : root.getConfigList(ConfigurationKeys.DOT11_TRAPS)) {
            if (!Dot11TrapDeviceDefinition.checkConfig(config)) {
                LOG.info("Skipping 802.11 trap device definition with invalid configuration. Invalid definition: [{}]", config);
                continue;
            }

            ImmutableList.Builder<Dot11TrapConfiguration> traps = new ImmutableList.Builder<>();
            for (Config trapConfig : config.getConfigList(ConfigurationKeys.TRAPS)) {
                traps.add(Dot11TrapConfiguration.create(
                        Trap.Type.valueOf(trapConfig.getString(ConfigurationKeys.TYPE)),
                        trapConfig
                ));
            }

            result.add(Dot11TrapDeviceDefinition.create(
                    config.getString(ConfigurationKeys.DEVICE_SENDER),
                    config.getIntList(ConfigurationKeys.CHANNELS),
                    config.getString(ConfigurationKeys.HOP_COMMAND),
                    config.getInt(ConfigurationKeys.HOP_INTERVAL),
                    traps.build()
            ));
        }

        return result.build();
    }

    private List<Alert.TYPE_WIDE> parseDot11Alerts() {
        ImmutableList.Builder<Alert.TYPE_WIDE> result = new ImmutableList.Builder<>();

        for (String alert : root.getStringList(ConfigurationKeys.DOT11_ALERTS)) {
            String name = alert.toUpperCase();

            if (Enums.getIfPresent(Alert.TYPE_WIDE.class, name).isPresent()) {
                result.add(Alert.TYPE_WIDE.valueOf(name));
            }
        }

        return result.build();
    }

    @Nullable
    private List<GraylogAddress> parseGraylogUplinks() {
        try {
            List<String> graylogAddresses = root.getStringList(ConfigurationKeys.GRAYLOG_UPLINKS);
            if (graylogAddresses == null) {
                return null;
            }

            List<GraylogAddress> result = Lists.newArrayList();
            for (String address : graylogAddresses) {
                String[] parts = address.split(":");
                result.add(GraylogAddress.create(parts[0], Integer.parseInt(parts[1])));
            }

            return result;
        } catch (ConfigException e) {
            LOG.debug(e);
            return null;
        }
    }

    @Nullable
    private TrackerDeviceConfiguration parseTrackerDevice() {
        if(trackerDevice.hasPath(ConfigurationKeys.TYPE)) {
            return TrackerDeviceConfiguration.create(
                    trackerDevice.getString(ConfigurationKeys.TYPE),
                    trackerDevice.getConfig(ConfigurationKeys.PARAMETERS)
            );
        } else {
            return null;
        }
    }

    private void validate() throws IncompleteConfigurationException, InvalidConfigurationException {
        // Completeness and type validity.
        ConfigurationValidator.expectEnum(general, ConfigurationKeys.ROLE, ConfigurationKeys.GENERAL, Role.class);
        ConfigurationValidator.expect(general, ConfigurationKeys.ID, ConfigurationKeys.GENERAL, String.class);
        ConfigurationValidator.expect(general, ConfigurationKeys.ADMIN_PASSWORD_HASH, ConfigurationKeys.GENERAL, String.class);
        ConfigurationValidator.expect(general, ConfigurationKeys.DATABASE_PATH, ConfigurationKeys.GENERAL, String.class);
        ConfigurationValidator.expect(general, ConfigurationKeys.VERSIONCHECKS, ConfigurationKeys.GENERAL, Boolean.class);
        ConfigurationValidator.expect(general, ConfigurationKeys.FETCH_OUIS, ConfigurationKeys.GENERAL, Boolean.class);
        ConfigurationValidator.expect(python, ConfigurationKeys.PYTHON_PATH, ConfigurationKeys.GENERAL + "." + ConfigurationKeys.PYTHON, String.class);
        ConfigurationValidator.expect(python, ConfigurationKeys.PYTHON_SCRIPT_DIR, ConfigurationKeys.GENERAL + "." + ConfigurationKeys.PYTHON, String.class);
        ConfigurationValidator.expect(python, ConfigurationKeys.PYTHON_SCRIPT_PREFIX, ConfigurationKeys.GENERAL + "." + ConfigurationKeys.PYTHON, String.class);
        ConfigurationValidator.expect(alerting, ConfigurationKeys.CLEAN_AFTER_MINUTES, ConfigurationKeys.GENERAL + "." + ConfigurationKeys.ALERTING, Integer.class);
        ConfigurationValidator.expect(alerting, ConfigurationKeys.TRAINING_PERIOD_SECONDS, ConfigurationKeys.GENERAL + "." + ConfigurationKeys.ALERTING, Integer.class);
        ConfigurationValidator.expect(interfaces, ConfigurationKeys.REST_LISTEN_URI, ConfigurationKeys.INTERFACES, String.class);
        ConfigurationValidator.expect(interfaces, ConfigurationKeys.HTTP_EXTERNAL_URI, ConfigurationKeys.INTERFACES, String.class);
        ConfigurationValidator.expect(root, ConfigurationKeys.DOT11_MONITORS, "<root>", List.class);
        ConfigurationValidator.expect(root, ConfigurationKeys.DOT11_NETWORKS, "<root>", List.class);
        ConfigurationValidator.expect(root, ConfigurationKeys.DOT11_ALERTS, "<root>", List.class);
        ConfigurationValidator.expect(root, ConfigurationKeys.TRACKER_DEVICE, "<root>", Config.class);

        if(trackerDevice.hasPath(ConfigurationKeys.TYPE)) {
            ConfigurationValidator.expect(trackerDevice, ConfigurationKeys.TYPE, ConfigurationKeys.TRACKER_DEVICE, String.class);
            ConfigurationValidator.expect(trackerDevice, ConfigurationKeys.PARAMETERS, ConfigurationKeys.TRACKER_DEVICE, Config.class);
        }

        // Password hash is 64 characters long (the size of a SHA256 hash string)
        if (parseAdminPasswordHash().length() != 64) {
            throw new InvalidConfigurationException("Parameter [general." + ConfigurationKeys.ADMIN_PASSWORD_HASH + "] must be 64 characters long (a SHA256 hash).");
        }

        // 802.11 Monitors.
        int i = 0;
        for (Config c : root.getConfigList(ConfigurationKeys.DOT11_MONITORS)) {
            String where = ConfigurationKeys.DOT11_MONITORS + "." + "#" + i;
            ConfigurationValidator.expect(c, ConfigurationKeys.DEVICE, where, String.class);
            ConfigurationValidator.expect(c, ConfigurationKeys.CHANNELS, where, List.class);
            ConfigurationValidator.expect(c, ConfigurationKeys.HOP_COMMAND, where, String.class);
            ConfigurationValidator.expect(c, ConfigurationKeys.HOP_INTERVAL, where, Integer.class);
            i++;
        }

        // 802.11 Trap Pairs
        i = 0;
        for (Config c : root.getConfigList(ConfigurationKeys.DOT11_TRAPS)) {
            String where = ConfigurationKeys.DOT11_TRAPS + ".#" + i;
            ConfigurationValidator.expect(c, ConfigurationKeys.DEVICE_SENDER, where, String.class);
            ConfigurationValidator.expect(c, ConfigurationKeys.CHANNELS, where, List.class);
            ConfigurationValidator.expect(c, ConfigurationKeys.HOP_COMMAND, where, String.class);
            ConfigurationValidator.expect(c, ConfigurationKeys.HOP_INTERVAL, where, Integer.class);
            ConfigurationValidator.expect(c, ConfigurationKeys.TRAPS, where, List.class);

            int y = 0;
            for (Config trap : c.getConfigList(ConfigurationKeys.TRAPS)) {
                String trapWhere = where + ".#" + y;

                // Make sure trap type exists and is set to an existing trap type.
                ConfigurationValidator.expect(trap, ConfigurationKeys.TYPE, trapWhere, String.class);
                String trapType = trap.getString(ConfigurationKeys.TYPE);
                try {
                    Trap.Type.valueOf(trapType);
                } catch(IllegalArgumentException e) {
                    throw new InvalidConfigurationException("Trap [" + trapWhere + "] is of invalid type [" + trapType + "].");
                }
                y++;
            }

        }

        // Logical validity.
        // Python: executable is an executable file.
        if(!Files.isExecutable(new File(parsePythonExecutable()).toPath())) {
            throw new InvalidConfigurationException("Parameter [general.python." + ConfigurationKeys.PYTHON_PATH + "] does not point to an executable file: " + parsePythonExecutable());
        }

        // Python: script directory is a directory and writable.
        if (!Files.isDirectory(new File(parsePythonScriptDirectory()).toPath()) || !Files.isWritable(new File(parsePythonScriptDirectory()).toPath())) {
            throw new InvalidConfigurationException("Parameter [general.python." + ConfigurationKeys.PYTHON_SCRIPT_DIR + "] does not point to a writable directory: " + parsePythonScriptDirectory());
        }

        // REST listen URI can be parsed into a URI.
        try {
            parseRestListenUri();
        } catch(Exception e) {
            LOG.error(e);
            throw new InvalidConfigurationException("Parameter [interfaces." + ConfigurationKeys.REST_LISTEN_URI + "] cannot be parsed into a URI. Make sure it is correct.");
        }

        // HTTP external URI can be parsed into a URI.
        try {
            parseHttpExternalUri();
        } catch(Exception e) {
            LOG.error(e);
            throw new InvalidConfigurationException("Parameter [interfaces." + ConfigurationKeys.HTTP_EXTERNAL_URI + "] cannot be parsed into a URI. Make sure it is correct.");
        }

        // TLS, if TLS is enabled.
        if (parseUseTls()) {
            // URI schemes must be HTTPS if TLS is enabled.
            if (!parseRestListenUri().getScheme().equals("https")) {
                throw new InvalidConfigurationException("TLS is enabled but [interfaces." + ConfigurationKeys.REST_LISTEN_URI + "] is not configured to use HTTPS.");
            }

            if (!parseHttpExternalUri().getScheme().equals("https")) {
                throw new InvalidConfigurationException("TLS is enabled but [interfaces." + ConfigurationKeys.HTTP_EXTERNAL_URI + "] is not configured to use HTTPS.");
            }

            try {
                Path cert = parseTlsCertificatePath();
                if (!cert.toFile().canRead()) {
                    throw new InvalidConfigurationException("Parameter [interfaces." + ConfigurationKeys.TLS_CERTIFICATE_PATH + "] points to a file that is not readable.");
                }
            } catch(Exception e) {
                LOG.error(e);
                throw new InvalidConfigurationException("Parameter [interfaces." + ConfigurationKeys.TLS_CERTIFICATE_PATH + "] cannot be parsed into a path. Make sure it is correct.");
            }

            try {
                Path key = parseTlsKeyPath();
                if (!key.toFile().canRead()) {
                    throw new InvalidConfigurationException("Parameter [interfaces." + ConfigurationKeys.TLS_KEY_PATH + "] points to a file that is not readable.");
                }
            } catch(Exception e) {
                LOG.error(e);
                throw new InvalidConfigurationException("Parameter [interfaces." + ConfigurationKeys.TLS_KEY_PATH + "] cannot be parsed into a path. Make sure it is correct.");
            }
        } else {
            // URI schemes must be HTTP if TLS is DISABLED..
            if (!parseRestListenUri().getScheme().equals("http")) {
                throw new InvalidConfigurationException("TLS is disabled but [interfaces." + ConfigurationKeys.REST_LISTEN_URI + "] is not configured to use HTTP. Do not use HTTPS.");
            }

            if (!parseHttpExternalUri().getScheme().equals("http")) {
                throw new InvalidConfigurationException("TLS is disabled but [interfaces." + ConfigurationKeys.HTTP_EXTERNAL_URI + "] is not configured to use HTTP. Do not use HTTPS.");
            }
        }

        // All channels are all integers, larger than 0.
        validateChannelList(ConfigurationKeys.DOT11_MONITORS);

        // 802_11 monitors should be parsed and safe to use for further logical checks from here on.

        // 802_11 monitors: No channel is used in any other monitor.
        List<Integer> usedChannels = Lists.newArrayList();
        for (Dot11MonitorDefinition monitor : parseDot11Monitors()) {
            for (Integer channel : monitor.channels()) {
                if (usedChannels.contains(channel)) {
                    throw new InvalidConfigurationException("Channel [" + channel + "] is defined for multiple 802.11 monitors. You should not have multiple monitors tuned to the same channel.");
                }
            }
            usedChannels.addAll(monitor.channels());
        }

        // 802_11 monitors: Device is not used in any other configuration.
        List<String> devices = Lists.newArrayList();
        for (Dot11MonitorDefinition monitor : parseDot11Monitors()) {
            if (devices.contains(monitor.device())) {
                throw new InvalidConfigurationException("Device [" + monitor.device() + "] is defined for multiple 802.11 monitors. You should not have multiple monitors using the same device.");
            }
            devices.add(monitor.device());
        }

        // 802_11 networks: SSID is unique.
        List<String> ssids = Lists.newArrayList();
        for (Dot11NetworkDefinition net : parseDot11Networks()) {
            if (ssids.contains(net.ssid())) {
                throw new InvalidConfigurationException("SSID [" + net.ssid() + "] is defined multiple times. You cannot define a network with the same SSID more than once.");
            }
            ssids.add(net.ssid());
        }

        // 802.11 networks: BSSIDs are unique for this network. (note that a BSSID can be used in multiple networks)
        for (Dot11NetworkDefinition net : parseDot11Networks()) {
            List<String> bssids = Lists.newArrayList();
            for (Dot11BSSIDDefinition bssid : net.bssids()) {
                if(bssids.contains(bssid.address())) {
                    throw new InvalidConfigurationException("Network [" + net.ssid() + "] has at least one BSSID defined twice. You cannot define a BSSID for the same network more than once.");
                }
                bssids.add(bssid.address());
            }
        }

        // TODO: No trap device is used multiple times or as a monitor.
    }

    private void validateChannelList(String key) throws InvalidConfigurationException {
        int x = 0;
        List<Integer> usedChannels = Lists.newArrayList();
        for (Config c : root.getConfigList(key)) {
            String where = key + "." + "#" + x;
            try {
                for (Integer channel : c.getIntList(ConfigurationKeys.CHANNELS)) {
                    if (channel < 1) {
                        throw new InvalidConfigurationException("Invalid channels in list for [" + where + "}. All channels must be integers larger than 0.");
                    }

                    if (usedChannels.contains(channel)) {
                        throw new InvalidConfigurationException("Duplicate channel <" + channel + "> in list for [ " + where + " ]. Channels cannot be duplicate per monitor or across multiple monitors.");
                    }

                    usedChannels.add(channel);
                }
            } catch(ConfigException e) {
                LOG.error(e);
                throw new InvalidConfigurationException("Invalid channels list for [" + where + "}. All channels must be integers larger than 0.");
            } finally {
                x++;
            }
        }
    }

}