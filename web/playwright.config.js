import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'./test/browser',fullyParallel:false,use:{baseURL:'http://127.0.0.1:8787',viewport:{width:390,height:844}},reporter:'list'});
