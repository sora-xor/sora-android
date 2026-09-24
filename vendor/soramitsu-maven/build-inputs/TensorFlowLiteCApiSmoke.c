#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>

#include "tensorflow/lite/c/c_api.h"

#define LOAD(sym)                                                \
  __typeof__(&sym) p_##sym = (__typeof__(&sym))dlsym(lib, #sym); \
  if (!p_##sym) {                                                \
    fprintf(stderr, "missing %s: %s\n", #sym, dlerror());       \
    return 2;                                                    \
  }

int main(int argc, char **argv) {
  if (argc != 3) {
    fprintf(stderr, "usage: %s library.so add.bin\n", argv[0]);
    return 2;
  }
  void *lib = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
  if (!lib) {
    fprintf(stderr, "dlopen: %s\n", dlerror());
    return 3;
  }
  LOAD(TfLiteVersion);
  LOAD(TfLiteModelCreateFromFile);
  LOAD(TfLiteModelDelete);
  LOAD(TfLiteInterpreterOptionsCreate);
  LOAD(TfLiteInterpreterOptionsDelete);
  LOAD(TfLiteInterpreterOptionsSetNumThreads);
  LOAD(TfLiteInterpreterCreate);
  LOAD(TfLiteInterpreterDelete);
  LOAD(TfLiteInterpreterAllocateTensors);
  LOAD(TfLiteInterpreterResizeInputTensor);
  LOAD(TfLiteInterpreterGetInputTensor);
  LOAD(TfLiteInterpreterGetOutputTensor);
  LOAD(TfLiteTensorCopyFromBuffer);
  LOAD(TfLiteTensorCopyToBuffer);
  LOAD(TfLiteInterpreterInvoke);
  LOAD(TfLiteTensorType);
  LOAD(TfLiteTensorNumDims);
  LOAD(TfLiteTensorDim);
  LOAD(TfLiteTensorByteSize);

  TfLiteModel *model = p_TfLiteModelCreateFromFile(argv[2]);
  if (!model) return 4;
  TfLiteInterpreterOptions *options = p_TfLiteInterpreterOptionsCreate();
  if (!options) return 5;
  p_TfLiteInterpreterOptionsSetNumThreads(options, 2);
  TfLiteInterpreter *interpreter = p_TfLiteInterpreterCreate(model, options);
  p_TfLiteInterpreterOptionsDelete(options);
  if (!interpreter) return 6;
  int shape[] = {2};
  if (p_TfLiteInterpreterResizeInputTensor(interpreter, 0, shape, 1) != kTfLiteOk ||
      p_TfLiteInterpreterAllocateTensors(interpreter) != kTfLiteOk) return 7;
  TfLiteTensor *input = p_TfLiteInterpreterGetInputTensor(interpreter, 0);
  if (!input || p_TfLiteTensorType(input) != kTfLiteFloat32 ||
      p_TfLiteTensorNumDims(input) != 1 || p_TfLiteTensorDim(input, 0) != 2 ||
      p_TfLiteTensorByteSize(input) != 2 * sizeof(float)) return 8;
  float values[] = {1.0f, 3.0f};
  if (p_TfLiteTensorCopyFromBuffer(input, values, sizeof(values)) != kTfLiteOk ||
      p_TfLiteInterpreterInvoke(interpreter) != kTfLiteOk) return 9;
  const TfLiteTensor *output = p_TfLiteInterpreterGetOutputTensor(interpreter, 0);
  float result[2] = {0};
  if (!output || p_TfLiteTensorCopyToBuffer(output, result, sizeof(result)) != kTfLiteOk)
    return 10;
  printf("version=%s output=%.1f,%.1f\n", p_TfLiteVersion(), result[0], result[1]);
  p_TfLiteInterpreterDelete(interpreter);
  p_TfLiteModelDelete(model);
  dlclose(lib);
  return (result[0] == 3.0f && result[1] == 9.0f) ? 0 : 11;
}
