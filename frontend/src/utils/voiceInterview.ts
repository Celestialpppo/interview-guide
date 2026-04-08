const ROLE_LABELS: Record<string, string> = {
  'ali-p8': '阿里P8后端面试',
  'byteance-algo': '字节算法工程师面试',
  'tencent-backend': '腾讯后台开发面试',
};

export function getRoleLabel(roleType: string): string {
  return ROLE_LABELS[roleType] || roleType;
}
