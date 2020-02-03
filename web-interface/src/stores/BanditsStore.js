import Reflux from 'reflux';

import RESTClient from "../util/RESTClient";
import BanditsActions from "../actions/BanditsActions";

class BanditsStore extends Reflux.Store {

    constructor() {
        super();
        this.listenables = BanditsActions;
    }

    onFindAll() {
        const self = this;

        RESTClient.get("/bandits", {}, function(response) {
            self.setState({bandits: response.data.bandits});
        });
    }

    onFindOne(id) {
        const self = this;

        RESTClient.get("/bandits/show/" + id, {}, function(response) {
            self.setState({bandit: response.data});
        });
    }

    onCreateBandit(name, description, successCallback, errorCallback) {
        RESTClient.post("/bandits", {name: name, description: description}, function() {
            successCallback();
        }, function() {
            errorCallback();
        });
    }

    onUpdateBandit(id, name, description, successCallback, errorCallback) {
        RESTClient.put("/bandits/update/" + id, {name: name, description: description}, function() {
            successCallback();
        }, function() {
            errorCallback();
        });
    }

    onFindAllIdentifierTypes() {
        const self = this;

        RESTClient.get("/bandits/identifiers/types", {}, function(response) {
            self.setState({banditIdentifierTypes: response.data.types});
        });
    }

}

export default BanditsStore;