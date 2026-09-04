/**
 * 测试专用最小 node 类型声明。
 * tsconfig 只面向浏览器（无 @types/node），rea-function-map-sync.test.ts 需要
 * fs/url/path 做导出同步，这里只声明用到的 API。
 */
declare module 'node:fs' {
  export function readFileSync(path: string, encoding: string): string;
  export function writeFileSync(path: string, data: string): void;
  export function mkdirSync(path: string, options: { recursive: boolean }): void;
}

declare module 'node:url' {
  export function fileURLToPath(url: string): string;
}

declare module 'node:path' {
  export function resolve(...segments: string[]): string;
  export function dirname(p: string): string;
}

declare const process: { env: Record<string, string | undefined> };
