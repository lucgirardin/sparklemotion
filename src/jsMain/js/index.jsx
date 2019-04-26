import React from 'react';
import ReactDOM from 'react-dom';

import App from './app';
import FakeClientDevice from './FakeClientDevice';
import styles from './FakeClientDevice.scss';

document.createUiApp = (container, uiContext) => {
  let app = <App uiContext={uiContext} />;
  ReactDOM.render(app, container);
  return app;
};

document.createFakeClientDevice = (name, content, onClose, onResize) => {
  let device = (
    <FakeClientDevice
      width={1024}
      height={768}
      content={content}
      onClose={onClose}
      onResize={onResize}
    />
  );
  let containerDiv = document.createElement('div');
  document.body.appendChild(containerDiv);

  let val = {
    device: device,
    close: () => {
      document.body.removeChild(containerDiv);
    },
  };

  ReactDOM.render(device, containerDiv, () => {
    let contentHolder = containerDiv.getElementsByClassName(
      styles['FakeClientDevice--content']
    )[0];
    val.contentNode = contentHolder;
    if (content != null) {
      contentHolder.appendChild(content);
      content.style.width = contentHolder.offsetWidth;
      content.style.height = contentHolder.offsetHeight;
    }

    setTimeout(() => {
      onResize(contentHolder.offsetWidth, contentHolder.offsetHeight);
    }, 0);
  });

  return val;
};

module.hot.accept();
