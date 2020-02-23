import React from 'react';
import Reflux from 'reflux';
import numeral from "numeral";
import moment from "moment";

class ContactsTableRow extends Reflux.Component {

    render() {
        const contact = this.props.contact;

        return (
            <tr>
                <td>{contact.uuid.substr(0, 8)}</td>
                <td>{contact.is_active ? <span className="badge badge-success">active</span> : <span className='badge badge-primary'>not active</span>}</td>
                <td>{numeral(contact.frame_count).format('0,0')}</td>
                <td title={moment(contact.first_seen).format()}>{moment(contact.first_seen).fromNow()}</td>
                <td title={moment(contact.last_Seen).format()}>{moment(contact.last_seen).fromNow()}</td>
            </tr>
        );
    }

}

export default ContactsTableRow;