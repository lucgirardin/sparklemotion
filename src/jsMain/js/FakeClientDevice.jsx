import React, { Component } from 'react';
import Draggable from 'react-draggable';
import styles from './FakeClientDevice.scss';

const BORDER_WIDTH = 10;

export default class FakeClientDevice extends Component {
  constructor(props) {
    super(props);

    this.onClose = props.onClose;
    this.onResize = props.onResize;

    this.clientDeviceContentRef = React.createRef();
  }

  get width() {
    return this.props.width / 2;
  }

  get height() {
    return this.props.height / 2;
  }

  get containerStyle() {
    return {
      width: `${this.width + BORDER_WIDTH * 2}px`,
      height: `${this.height + BORDER_WIDTH * 2}px`,
    };
  }

  get contentStyle() {
    return {
      width: `${this.width}px`,
      height: `${this.height}px`,
    };
  }

  onZoomOut = () => {
    console.log('TODO: Implement onZoomOut');
  };

  onZoomIn = () => {
    console.log('TODO: Implement onZoomin');
  };

  onDragStart = (e, draggableData) => {
    const draggableNode = draggableData.node;
    const contentNode = this.clientDeviceContentRef.current;
    let eventNode = e.target;

    while (eventNode != null) {
      if (eventNode === contentNode) {
        return false;
      } else if (eventNode === draggableNode) {
        return true;
      } else {
        eventNode = eventNode.parentNode;
      }
    }
  };

  render() {
    return (
      <Draggable
        onStart={(e, data) => {
          return this.onDragStart(e, data);
        }}
      >
        <div
          className={styles['FakeClientDevice--pad']}
          style={this.containerStyle}
        >
          <div className={styles['FakeClientDevice-controls']}>
            <i className="fas fa-search-minus" onClick={this.onZoomOut} />
            <i className="fas fa-search-plus" onClick={this.onZoomIn} />
            &nbsp;
            <i className="far fa-times-circle" onClick={this.onClose} />
          </div>
          <div
            ref={this.clientDeviceContentRef}
            className={styles['FakeClientDevice--content']}
            style={this.contentStyle}
          />
        </div>
      </Draggable>
    );
  }
}