import React from 'react';
import Reflux from 'reflux';
import moment from "moment";
import Routes from "../../util/Routes";

class BanditsTableRow extends Reflux.Component {

    constructor(props) {
        super(props);

        this.state = {
            bandit: this.props.bandit
        }
    }

    render() {
        const bandit = this.state.bandit;

        return (
            <tr>
                <td><a href={Routes.BANDITS.SHOW(bandit.uuid)}>{bandit.name}</a></td>
                <td>{bandit.is_active ? <span className="badge badge-success">active</span> : <span className='badge badge-primary'>not active</span>}</td>
                <td title={bandit.last_contact ? moment(bandit.last_contact).format() : "never"}>
                    {bandit.last_contact ? moment(bandit.last_contact).fromNow() : "never"}
                </td>
                <td title={moment(bandit.created_at).format()}>{moment(bandit.created_at).fromNow()}</td>
                <td title={moment(bandit.updated_at).format()}>{moment(bandit.updated_at).fromNow()}</td>
            </tr>
        );
    }

}

export default BanditsTableRow;