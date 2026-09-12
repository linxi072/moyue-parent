import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/dist/index.css';
import App from './App.vue';
import router from './router';
// 主题必须在 element-plus 样式之后引入，才能覆盖其 CSS 变量
import './styles/theme.css';

const app = createApp(App);

app.use(createPinia());
app.use(router);
// 中文语言包：分页器、日期选择器、表格空态等默认文案全部汉化
app.use(ElementPlus, { locale: zhCn });
app.mount('#app');
