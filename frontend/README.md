# 巷里 H5 前端

基于 React、TypeScript 和 Vite 的手机端探店原型。视觉采用深梅色与珊瑚色方案；首页、搜索、探店故事、店铺、限时优惠、演示下单、发布、消息和个人页均可操作。

## 本地运行

```powershell
npm ci
npm run dev
```

启动后打开终端显示的本地地址。页面带 iPhone / Pixel 预览框，可切换设备查看；正式 H5 页面与服务端联调仍属下一阶段。

## 检查

```powershell
npm run check:runtime
npm run build
```

原型数据保存在 React 页面状态中，刷新后重置。下单状态是前端演示，不会产生真实订单或支付。完整范围、后续接口与 RabbitMQ 方案见 [需求文档.md](./需求文档.md)。视觉比对见 [design-qa.md](./design-qa.md)。
