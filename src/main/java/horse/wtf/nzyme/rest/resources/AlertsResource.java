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

package horse.wtf.nzyme.rest.resources;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import horse.wtf.nzyme.Nzyme;
import horse.wtf.nzyme.alerts.Alert;
import horse.wtf.nzyme.rest.responses.alerts.AlertConfigurationResponse;
import horse.wtf.nzyme.rest.responses.alerts.AlertDetailsResponse;
import horse.wtf.nzyme.rest.responses.alerts.AlertsListResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/alerts")
@Produces(MediaType.APPLICATION_JSON)
public class AlertsResource {

    private static final Logger LOG = LogManager.getLogger(AlertsResource.class);

    @Inject
    private Nzyme nzyme;

    @GET
    @Path("/configuration")
    public Response configured() {
        ImmutableList.Builder<Alert.TYPE_WIDE> enabled = new ImmutableList.Builder<>();
        ImmutableList.Builder<Alert.TYPE_WIDE> disabled = new ImmutableList.Builder<>();

        for (Alert.TYPE_WIDE type : Alert.TYPE_WIDE.values()) {
            if (nzyme.getConfiguration().getDot11Alerts().contains(type)) {
                enabled.add(type);
            } else {
                disabled.add(type);
            }
        }

        return Response.ok(AlertConfigurationResponse.create(enabled.build(), disabled.build())).build();
    }

    @GET
    @Path("/active")
    public Response active(@QueryParam("limit") int limit) {
        if(limit == 0) {
            limit = 25;
        }

        Map<UUID, Alert> alerts = nzyme.getAlertsService().getActiveAlerts();

        List<AlertDetailsResponse> details = Lists.newArrayList();
        int i = 0;
        for (Map.Entry<UUID, Alert> entry : alerts.entrySet()) {
            if (i == limit) {
                break;
            }
            i++;

            Alert alert = entry.getValue();
            details.add(AlertDetailsResponse.fromAlert(alert));
        }

        return Response.ok(AlertsListResponse.create(alerts.size(), details)).build();
    }

    @GET
    @Path("/show/{id}")
    public Response get(@PathParam("id") String id) {
        if (Strings.isNullOrEmpty(id)) {
            LOG.warn("Alert ID was null or empty.");
            return Response.status(401).build();
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch(IllegalArgumentException e) {
            LOG.warn("Invalid UUID [{}].", id, e);
            return Response.status(401).build();
        }

        Alert alert = nzyme.getAlertsService().getActiveAlerts().get(uuid);
        if (alert != null) {
            return Response.ok(AlertDetailsResponse.fromAlert(alert)).build();
        } else {
            return Response.status(404).build();
        }
    }

}