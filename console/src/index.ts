import { definePlugin } from "@runikaros/shared"
import S3Guide from '@/views/S3Guide.vue';
import { Files as FilesIcon } from '@element-plus/icons-vue';
import { markRaw } from "vue"

export default definePlugin({
    name: 'PluginS3',
    components: {},
    routes: [
      {
        parentName: "Root",
        route: {
          path: '/PluginS3',
          component: S3Guide,
          name: "S3Guide",
          meta: {
            title: 'S3对象存储',
            menu: {
              name: 'S3对象存储',
              group: 'content',
              icon: markRaw(FilesIcon),
              priority: 2,
              mobile: true,
            }
          }
        }
      }
    ],
})