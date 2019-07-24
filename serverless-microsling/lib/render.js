/* 
 * Copyright 2019 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 /* eslint-disable no-console */

async function render(context) {
  const resource = context.content.resource.content;
  console.log(resource);
  const markup = `
    <html>
    <head>
    <title>${resource.title}</title>
    </head>
    <body>
    <h1>
        ${resource.title}
    </h1>
    <div>${resource.body}</div>
    </body>
    </html>
  `;
  context.response.body = markup;
  context.response.headers = {
    'Content-Type': 'text/html',
  };
  return context;
}

module.exports.render = render;